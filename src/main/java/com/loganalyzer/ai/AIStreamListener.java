package com.loganalyzer.ai;

@FunctionalInterface
public interface AIStreamListener {

    AIStreamListener NO_OP = new AIStreamListener() {
        @Override
        public void onToken(String token) {
            // Intentionally no-op.
        }

        @Override
        public boolean isEnabled() {
            return false;
        }
    };

    void onToken(String token);

    default boolean isEnabled() {
        return true;
    }
}

