package com.example.pendingupdates.adapter.inmemory;

import com.example.pendingupdates.port.BindingRepository;
import com.example.pendingupdates.port.BindingRepositoryContract;

class InMemoryBindingRepositoryTest extends BindingRepositoryContract {

    @Override
    protected BindingRepository newRepository() {
        return new InMemoryBindingRepository();
    }
}
