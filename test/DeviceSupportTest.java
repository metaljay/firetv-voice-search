import com.metaljay.voicesearchtile.DeviceSupport;

public class DeviceSupportTest {
    static int fail = 0;

    static void check(String label, Object expected, Object actual) {
        if (expected.equals(actual)) System.out.println("PASS " + label + " -> " + actual);
        else { System.out.println("FAIL " + label + " -> " + actual + " (expected " + expected + ")"); fail++; }
    }

    public static void main(String[] a) {
        // Tested
        check("4K Max 2nd gen, 8.1.8.5", true, DeviceSupport.isTested("AFTKRT", "8.1.8.5"));
        check("4K Max 2nd gen, 8.1.8.0", true, DeviceSupport.isTested("AFTKRT", "8.1.8.0"));
        check("model case and spaces", true, DeviceSupport.isTested(" aftkrt ", " 8.1.8.5 "));
        // Untested
        check("4K Max 2nd gen, newer Fire OS", false, DeviceSupport.isTested("AFTKRT", "8.1.9.0"));
        check("4K Max 2nd gen, older Fire OS", false, DeviceSupport.isTested("AFTKRT", "8.1.7.9"));
        check("4K Max 2nd gen, Fire OS 7", false, DeviceSupport.isTested("AFTKRT", "7.2.8.5"));
        check("other model, tested Fire OS", false, DeviceSupport.isTested("AFTMM", "8.1.8.5"));
        check("lookalike prefix 8.1.80.1", false, DeviceSupport.isTested("AFTKRT", "8.1.80.1"));
        // Odd strings
        check("null model", false, DeviceSupport.isTested(null, "8.1.8.5"));
        check("null version", false, DeviceSupport.isTested("AFTKRT", null));
        check("empty both", false, DeviceSupport.isTested("", ""));
        check("just the prefix", false, DeviceSupport.isTested("AFTKRT", "8.1.8."));
        check("words, not a version", false, DeviceSupport.isTested("AFTKRT", "Fire OS"));
        check("lookalike model", false, DeviceSupport.isTested("AFTKRTX", "8.1.8.5"));
        // Reading the version out of what the system reports
        check("plain", "8.1.8.5", DeviceSupport.versionFrom("8.1.8.5"));
        check("with words", "8.1.8.5", DeviceSupport.versionFrom("Fire OS 8.1.8.5 (PS8185/2517)"));
        check("two parts only", "", DeviceSupport.versionFrom("8.1"));
        check("null", "", DeviceSupport.versionFrom(null));
        check("empty", "", DeviceSupport.versionFrom(""));
        check("nonsense", "", DeviceSupport.versionFrom("unknown"));
        check("describe", "AFTKRT, Fire OS 8.1.8.5", DeviceSupport.describe("AFTKRT", "8.1.8.5"));
        check("describe unknown", "unknown model, unknown Fire OS version", DeviceSupport.describe(null, ""));
        if (fail > 0) { System.out.println(fail + " DeviceSupport case(s) FAILED."); System.exit(1); }
        System.out.println("All DeviceSupport cases pass.");
    }
}
