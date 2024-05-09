package com.biddflux.agent.config;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;

import com.biddflux.agent.api.ApiClientReactive;

@Configuration
public class AotHints implements RuntimeHintsRegistrar{

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.reflection().registerType(ApiClientReactive.class, t -> t.withField("webClient"));
    }

}
