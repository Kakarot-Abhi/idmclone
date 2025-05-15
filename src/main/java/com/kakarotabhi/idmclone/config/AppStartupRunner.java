package com.kakarotabhi.idmclone.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
public class AppStartupRunner implements CommandLineRunner {
    @Value("${download.dir:/download/IDMClone}")
    private String downloadDir;

    @Override
    public void run(String... args) {
        File dir = new File(downloadDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }
}
