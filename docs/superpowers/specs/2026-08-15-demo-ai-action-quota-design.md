# Demo AI Action Quota Design

**Date:** 2026-08-15
**Status:** Approved

## Goal

Make the demo action limit deployment-configurable, default it to 15, and count every admitted
backend AI operation consistently. Receipt processing, speech-token acquisition, and each chatbot
message admitted to Azure OpenAI cost one action. Saving a resulting expense remains a separate
one-action mutation.

## Scope

This design spans:

- `expense-tracker-api`, which owns demo sessions, quota, Direct Line identity mappings, and the
  authoritative action ledger.
- `expense-tracker-chatbot`, which receives Direct Line activities and calls Azure OpenAI.
- `expense-tracker-web`, which displays server-provided quota metadata.

Personal sessions remain unmetered. Existing demo admission windows, session capacity, expiry,
renewal, logout, data isolation, and disabled-feature behavior do not change.

## Configurable action limit

The Expense API reads `DEMO_ACTION_LIMIT` through `demo.action-limit`. The default is 15 and the
configured positive integer applies immediately to active and future sessions. A missing value uses
the default; a nonpositive or malformed value fails application startup. Existing used and reserved
counts are preserved. If the configured limit is lowered below an active session's consumption, its
remaining count is zero and later charged operations are rejected.

The API exposes the configured limit and derived remaining count in session grants, quota snapshots,
and `Demo-Actions-*` headers. Production web code continues to use only server-provided metadata.

Static database constraints no longer encode a deployment-specific upper limit. A forward migration
retains nonnegative constraints for used and reserved actions and a positive-cost constraint for
reservations. The existing session locks and transactions remain the authoritative configured-limit
enforcement boundary.

## Speech and receipt accounting

Speech-token prefetch remains enabled for personal and demo sessions so Voice startup behavior stays
consistent. A successful demo speech-token request finalizes one reserved action even when the token
was prefetched and Voice is not later used. A provider failure releases the reservation and costs
zero. Saving a voice-derived expense is a normal mutation and costs one additional action.

Successful receipt AI processing finalizes one reserved action. A provider failure releases the
reservation and costs zero. Saving the receipt-derived draft is a normal mutation and costs one
additional action.

Therefore, successful processing plus saving costs two actions for either workflow.

## Chatbot claim protocol

The chatbot service must claim a message with the Expense API after local input validation and
per-conversation rate limiting but immediately before its first Azure OpenAI request.

The authenticated internal claim request contains:

- Direct Line user ID.
- Direct Line conversation ID.
- Direct Line activity ID.

The Expense API selects the data realm from the Direct Line user ID, resolves the authoritative
identity mapping, and returns one of:

- `PERSONAL`: valid personal mapping; no demo action is charged.
- `CLAIMED`: valid demo mapping; one action is committed.
- `DUPLICATE`: that activity ID was already claimed for the demo session; no action is charged.

For a demo mapping, the API locks the active session and checks for an existing idempotency record
before checking available quota. If the activity is new, it checks quota, inserts the record, and
increments used actions in the same transaction. The idempotency key is the demo session plus Direct
Line activity ID, backed by a database uniqueness constraint. This ordering means a redelivery is
still reported as `DUPLICATE` when the first delivery consumed the session's last action. Session
cleanup deletes these records with the session.

Quota exhaustion rejects the claim before Azure OpenAI. Missing or invalid identity also rejects it.
The chatbot fails closed when the claim API is unavailable. No rejected or duplicate claim may invoke
Azure OpenAI.

Once a claim commits, its action remains used even if Azure OpenAI fails. A duplicate delivery does
not invoke Azure OpenAI again; the chatbot returns a short already-processed response. Empty,
oversized, locally rate-limited, and otherwise locally invalid messages are rejected before claim and
cost zero.

## Quota synchronization in the web client

The Expense API exposes a protected current-demo-quota endpoint. It returns the authoritative limit,
used actions, remaining actions, and session expiry for the authenticated demo session.

After the chatbot emits a message replying to a user's activity, the web client requests this quota
snapshot. The existing API client applies the response quota headers to the auth store, so the topbar
updates without browser-side arithmetic. Welcome activities and typing activities do not trigger a
refresh. A refresh failure does not break or hide the chatbot reply; the next successful API request
reconciles metadata.

## Failure behavior

- Demo quota exhaustion prevents speech-token issuance, receipt AI processing, and chatbot model
  calls before their external provider call.
- Speech and receipt provider failures release their reservations.
- Chatbot model failure after a successful claim keeps the action consumed.
- Chat claim identity, quota, or availability failures produce safe user-facing chatbot messages and
  do not expose internal response content.
- Personal traffic remains unmetered and retains its existing error behavior.

## Verification

Automated tests prove:

- The default configured action limit is 15 and an override applies immediately.
- Session grants, quota enforcement, external reservations, and quota headers use the configured
  value.
- Lowering the limit below existing usage yields zero remaining actions without rewriting usage.
- Concurrent chatbot claims cannot exceed quota.
- A repeated activity ID charges once and never invokes Azure OpenAI twice.
- Empty, oversized, invalid-identity, locally rate-limited, quota-rejected, and claim-unavailable
  messages do not invoke Azure OpenAI.
- An Azure OpenAI failure after a successful claim still consumes one action.
- Successful speech or receipt processing followed by expense save consumes two actions.
- Demo chatbot replies trigger authoritative web quota refresh; welcome, typing, and personal flows
  do not.
- API, chatbot, and web full suites, typechecks, and builds remain green.
