package io.github.skydynamic.quickbackupmulti;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ModVersion implements Comparable<ModVersion> {
    public int major;
    public int minor;
    public int patch;
    public int buildNumber = 0;

    public boolean release = false;
    public BuildType buildType = null;

    public ModVersion(int major, int minor, int patch, int buildNumber, boolean release, BuildType buildType) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.buildNumber = buildNumber;
        this.release = release;
        this.buildType = buildType;
    }

    public ModVersion(String version) {
        this.parse(version);
    }

    public void parse(String version) {
        String regex = "^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:[-+](snapshot|build\\.(\\d+)|alpha(?:\\.(\\d+))?|beta(?:\\.(\\d+))?))?$";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(version);

        if (matcher.matches()) {
            this.major = Integer.parseInt(matcher.group(1));
            this.minor = Integer.parseInt(matcher.group(2));
            this.patch = Integer.parseInt(matcher.group(3));

            String suffix = matcher.group(4);
            if (suffix != null) {
                this.release = false;
                if ("snapshot".equals(suffix)) {
                    this.buildNumber = -1;
                    this.buildType = BuildType.SNAPSHOT;
                } else if (suffix.startsWith("build.")) {
                    this.buildNumber = Integer.parseInt(matcher.group(5));
                    this.buildType = BuildType.DEV;
                } else if (suffix.startsWith("alpha")) {
                    String alphaBuild = matcher.group(6);
                    if (alphaBuild != null) {
                        this.buildNumber = Integer.parseInt(alphaBuild);
                    } else {
                        this.buildNumber = 0;
                    }
                    this.buildType = BuildType.ALPHA;
                } else if (suffix.startsWith("beta")) {
                    String betaBuild = matcher.group(7);
                    if (betaBuild != null) {
                        this.buildNumber = Integer.parseInt(betaBuild);
                    } else {
                        this.buildNumber = 0;
                    }
                    this.buildType = BuildType.BETA;
                }
            } else {
                this.release = true;
            }
        } else {
            throw new IllegalArgumentException("Invalid version format: " + version);
        }
    }


    public boolean isNewerThan(ModVersion other) {
        return this.compareTo(other) > 0;
    }

    public enum BuildType {
        ALPHA(0),
        BETA(1),
        DEV(2),
        SNAPSHOT(3);

        public final int priority;

        BuildType(int priority) {
            this.priority = priority;
        }

        public int compare(ModVersion o) {
            return Integer.compare(this.priority, o.buildType.priority);
        }
    }

    @Override
    public int compareTo(ModVersion other) {
        int majorCompare = Integer.compare(this.major, other.major);
        if (majorCompare != 0) return majorCompare;

        int minorCompare = Integer.compare(this.minor, other.minor);
        if (minorCompare != 0) return minorCompare;

        int patchCompare = Integer.compare(this.patch, other.patch);
        if (patchCompare != 0) return patchCompare;

        if (this.release && !other.release) return 1;
        if (!this.release && other.release) return -1;

        if (!this.release) {
            int r = other.buildType.compare(this);
            if (r == 0) {
                if (this.buildNumber == -1 && other.buildNumber >= 0) return -1;
                if (this.buildNumber >= 0 && other.buildNumber == -1) return 1;
                return Integer.compare(this.buildNumber, other.buildNumber);
            }
            return r;
        }

        return 0;
    }

    @Override
    public String toString() {
        String baseVersion = this.major + "." + this.minor + "." + this.patch;

        if (this.release) {
            return baseVersion;
        } else {
            switch (this.buildType) {
                case SNAPSHOT:
                    return baseVersion + "+snapshot";
                case ALPHA:
                    if (this.buildNumber > 0) {
                        return baseVersion + "+alpha." + this.buildNumber;
                    } else {
                        return baseVersion + "+alpha";
                    }
                case BETA:
                    if (this.buildNumber > 0) {
                        return baseVersion + "+beta." + this.buildNumber;
                    } else {
                        return baseVersion + "+beta";
                    }
                case DEV:
                    return baseVersion + "+build." + this.buildNumber;
                default:
                    return baseVersion;
            }
        }
    }
}
