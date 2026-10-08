package jvbot;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Loads and saves settings in settings.properties (plain text, no database).
 *
 * The central idea is a list of PROFILES: each one pairs a model with the
 * API key the user added for it ("profiles" property, comma-separated
 * "model|key" entries). The active profile's model and key are mirrored
 * into the old "model" and "api.key" properties, which keeps NvidiaClient
 * and old settings files working unchanged.
 */
public class Settings {

    public static final String DEFAULT_MODEL = "google/gemma-3n-e4b-it";

    /** One saved combination: a model plus the key it runs on. */
    public static class Profile {
        public final String model;
        public final String key;

        public Profile(String model, String key) {
            this.model = model;
            this.key = key;
        }
    }

    private final Path file;
    private final Properties props = new Properties();

    public Settings(Path file) {
        this.file = file;
        load();
    }

    public void load() {
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                props.load(in);
            } catch (IOException e) {
                System.err.println("[warn] Could not read settings: " + e.getMessage());
            }
        }
        migrate();
    }

    /** Old files had api.key / api.keys but no profiles - build them once. */
    private void migrate() {
        if (!getProfiles().isEmpty()) return;
        List<String> keys = new ArrayList<>();
        for (String k : props.getProperty("api.keys", props.getProperty("api.key", "")).split(",")) {
            String key = k.trim();
            if (!key.isEmpty() && !keys.contains(key)) keys.add(key);
        }
        if (keys.isEmpty()) return;
        List<Profile> profiles = new ArrayList<>();
        for (String key : keys) {
            profiles.add(new Profile(getModel(), key));
        }
        int active = Math.max(0, keys.indexOf(props.getProperty("api.key", "").trim()));
        store(profiles, active);
    }

    public void save() {
        try (OutputStream out = Files.newOutputStream(file)) {
            props.store(out, "JVBot settings");
        } catch (IOException e) {
            System.err.println("[warn] Could not save settings: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------- profiles

    public List<Profile> getProfiles() {
        List<Profile> out = new ArrayList<>();
        for (String entry : props.getProperty("profiles", "").split(",")) {
            int bar = entry.indexOf('|');
            if (bar > 0) {
                String model = entry.substring(0, bar).trim();
                String key = entry.substring(bar + 1).trim();
                if (!model.isEmpty() && !key.isEmpty()) out.add(new Profile(model, key));
            }
        }
        return out;
    }

    /** Index of the active profile, or -1 when none is saved. */
    public int getActiveProfile() {
        try {
            int i = Integer.parseInt(props.getProperty("profile.active", "-1").trim());
            return (i >= 0 && i < getProfiles().size()) ? i : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Saves a model+key pair (no duplicates) and makes it active. */
    public void addProfile(String model, String key) {
        model = model == null || model.trim().isEmpty() ? DEFAULT_MODEL : model.trim();
        key = key == null ? "" : key.trim();
        if (key.isEmpty()) return;
        List<Profile> profiles = getProfiles();
        int found = -1;
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).model.equals(model) && profiles.get(i).key.equals(key)) {
                found = i;
                break;
            }
        }
        if (found < 0) {
            profiles.add(new Profile(model, key));
            found = profiles.size() - 1;
        }
        store(profiles, found);
    }

    public void removeProfile(int index) {
        List<Profile> profiles = getProfiles();
        if (index < 0 || index >= profiles.size()) return;
        int active = getActiveProfile();
        profiles.remove(index);
        if (active == index) active = profiles.isEmpty() ? -1 : 0;
        else if (active > index) active--;
        store(profiles, active);
    }

    public void setActiveProfile(int index) {
        List<Profile> profiles = getProfiles();
        if (index >= 0 && index < profiles.size()) {
            store(profiles, index);
        }
    }

    private void store(List<Profile> profiles, int active) {
        StringBuilder sb = new StringBuilder();
        for (Profile p : profiles) {
            if (sb.length() > 0) sb.append(',');
            sb.append(p.model).append('|').append(p.key);
        }
        props.setProperty("profiles", sb.toString());
        props.setProperty("profile.active", String.valueOf(active));
        if (active >= 0) {
            props.setProperty("api.key", profiles.get(active).key);
            props.setProperty("model", profiles.get(active).model);
        } else {
            props.setProperty("api.key", "");
        }
        save();
    }

    // ------------------------------------- active values (used everywhere)

    /** The ACTIVE key (empty string if none). */
    public String getKey() {
        return props.getProperty("api.key", "").trim();
    }

    public boolean hasKey() {
        return !getKey().isEmpty();
    }

    /** Console /key: pairs the key with the current model and activates it. */
    public void setKey(String key) {
        addProfile(getModel(), key);
    }

    public String getModel() {
        return props.getProperty("model", DEFAULT_MODEL).trim();
    }

    /** Changes the active model; the active profile follows along. */
    public void setModel(String model) {
        model = model.trim();
        int active = getActiveProfile();
        if (active >= 0) {
            List<Profile> profiles = getProfiles();
            profiles.set(active, new Profile(model, profiles.get(active).key));
            store(profiles, active);
        } else {
            props.setProperty("model", model);
            save();
        }
    }
}
