package com.example.expensetracker.demo.session;

import com.example.expensetracker.demo.security.DemoPrincipal;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo/sessions")
@Profile("prod")
public class DemoSessionController {

    static final String RESUME_COOKIE = "demo_resume";
    private static final String COOKIE_PATH = "/api/demo/sessions";

    private final DemoSessionFacade facade;
    private final String sameSite;

    public DemoSessionController(
        DemoSessionFacade facade,
        @Value("${demo.resume-cookie.same-site:Lax}") String sameSite
    ) {
        this.facade = facade;
        this.sameSite = "none".equalsIgnoreCase(sameSite) ? "None" : "Lax";
    }

    @PostMapping
    public ResponseEntity<DemoSessionResponse> createOrResume(
        @CookieValue(name = RESUME_COOKIE, required = false) String rawResumeCookie
    ) {
        return grantResponse(facade.createOrResume(rawResumeCookie));
    }

    @PostMapping("/renew")
    public ResponseEntity<DemoSessionResponse> renew(
        @CookieValue(name = RESUME_COOKIE, required = false) String rawResumeCookie
    ) {
        return grantResponse(facade.renew(rawResumeCookie));
    }

    private ResponseEntity<DemoSessionResponse> grantResponse(
        DemoSessionService.SessionGrant grant
    ) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .header(HttpHeaders.SET_COOKIE, resumeCookie(grant).toString())
            .body(grant.response());
    }

    @DeleteMapping("/current")
    public ResponseEntity<Void> logout(Authentication authentication) {
        facade.logout(sessionId(authentication));
        return ResponseEntity.noContent()
            .cacheControl(CacheControl.noStore())
            .header(HttpHeaders.SET_COOKIE, clearedResumeCookie().toString())
            .build();
    }

    private ResponseCookie resumeCookie(DemoSessionService.SessionGrant grant) {
        return cookie(grant.resumeToken())
            .maxAge(Duration.ofSeconds(grant.resumeCookieMaxAgeSeconds()))
            .build();
    }

    private ResponseCookie clearedResumeCookie() {
        return cookie("")
            .maxAge(Duration.ZERO)
            .build();
    }

    private ResponseCookie.ResponseCookieBuilder cookie(String value) {
        return ResponseCookie.from(RESUME_COOKIE, value)
            .httpOnly(true)
            .secure(true)
            .path(COOKIE_PATH)
            .sameSite(sameSite);
    }

    private static UUID sessionId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof DemoPrincipal principal) {
            return principal.sessionId();
        }
        throw DemoSessionException.sessionExpired();
    }
}
