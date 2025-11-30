package com.raceplayback.raceplaybackserver.data;

public enum SessionType {
    FP1("free practice 1"),
    FP2("free practice 2"),
    FP3("free practice 3"),
    Q("qualifying"),
    SQ("sprint qualifying"),
    S("sprint"),
    R("race"),
    P("pre-season testing");

    private final String fullName;

    SessionType(String fullName) {
        this.fullName = fullName;
    }

    public String getFullName() {
        return fullName;
    }

    public String getTitleCase() {
        String[] words = this.fullName.split(" ");
        for (int i = 0; i < words.length; i++) {
            if (!words[i].isEmpty()) {
                words[i] = words[i].substring(0, 1).toUpperCase() + words[i].substring(1);
            }
        }
        return String.join(" ", words);
    }
}
