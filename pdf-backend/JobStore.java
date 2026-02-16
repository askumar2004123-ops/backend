package com.example.pdfbackend;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class JobStore {

    public static class Job {
        public String id;
        public volatile int progress;
        public volatile String status;
        public volatile String message;
        public volatile byte[] result;
        public volatile String filename;
    }

    private static final Map<String, Job> JOBS = new ConcurrentHashMap<>();

    public static Job create(String filename) {
        Job j = new Job();
        j.id = UUID.randomUUID().toString();
        j.progress = 0;
        j.status = "QUEUED";
        j.message = "Queued";
        j.filename = filename;
        JOBS.put(j.id, j);
        return j;
    }

    public static Job get(String id) {
        return JOBS.get(id);
    }
}
