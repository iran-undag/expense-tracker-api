package com.example.expensetracker.persistence;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

public class RealmRoutingDataSource extends AbstractRoutingDataSource {
    @Override
    protected Object determineCurrentLookupKey() {
        return DataRealmContext.current().orElse(DataRealm.PRIMARY);
    }
}
