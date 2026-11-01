import java.text.Normalizer;
import java.util.*;

public class Probe {
    static final Set<String> COMMON = Set.of("123456", "password", "qwerty", "contraseña", "111111", "admin", "iloveyou", "000000", "abc123", "12345678");
    static final String[] ROWS = {"qwertyuiop", "asdfghjkl", "zxcvbnm", "1234567890"};

    // ---- old: UTF-16 units (copy of Health.kt today) ----
    static double oldBits(String p) {
        if (p.isEmpty() || COMMON.contains(p.toLowerCase(Locale.ROOT))) return 0.0;
        int pool = 0;
        if (p.chars().anyMatch(c -> c >= 'a' && c <= 'z')) pool += 26;
        if (p.chars().anyMatch(c -> c >= 'A' && c <= 'Z')) pool += 26;
        if (p.chars().anyMatch(c -> c >= '0' && c <= '9')) pool += 10;
        if (p.chars().anyMatch(c -> c < 128 && !Character.isLetterOrDigit((char) c))) pool += 33;
        if (p.chars().anyMatch(c -> c >= 128)) pool += 100;
        return effLen(p.chars().toArray()) * Math.log(pool) / Math.log(2.0);
    }
    // ---- new, literal N-02: NFC, no Cf, code points ----
    static double newBits(String password) {
        if (password.isEmpty() || COMMON.contains(password.toLowerCase(Locale.ROOT))) return 0.0;
        String n = Normalizer.normalize(password, Normalizer.Form.NFC);
        StringBuilder sb = new StringBuilder();
        n.codePoints().filter(c -> Character.getType(c) != Character.FORMAT).forEach(sb::appendCodePoint);
        String p = sb.toString();
        if (p.isEmpty() || COMMON.contains(p.toLowerCase(Locale.ROOT))) return 0.0;
        int[] cps = p.codePoints().toArray();
        int pool = 0;
        if (Arrays.stream(cps).anyMatch(c -> c >= 'a' && c <= 'z')) pool += 26;
        if (Arrays.stream(cps).anyMatch(c -> c >= 'A' && c <= 'Z')) pool += 26;
        if (Arrays.stream(cps).anyMatch(c -> c >= '0' && c <= '9')) pool += 10;
        if (Arrays.stream(cps).anyMatch(c -> c < 128 && !Character.isLetterOrDigit(c))) pool += 33;
        if (Arrays.stream(cps).anyMatch(c -> c >= 128)) pool += 100;
        return effLen(cps) * Math.log(pool) / Math.log(2.0);
    }
    static int step(int a, int b) {
        if (a == b) return 0;
        int x = Character.toLowerCase(a), y = Character.toLowerCase(b);
        if (Character.isLetterOrDigit(x) && Character.isLetterOrDigit(y) && x - y >= -1 && x - y <= 1) return y - x;
        for (String row : ROWS) {
            int i = x < 128 ? row.indexOf(x) : -1, j = y < 128 ? row.indexOf(y) : -1;
            if (i >= 0 && j >= 0 && i - j >= -1 && i - j <= 1) return j - i;
        }
        return Integer.MIN_VALUE;
    }
    static int effLen(int[] p) {
        int count = 0, i = 0;
        while (i < p.length) {
            int end = i;
            if (i + 1 < p.length) {
                int d = step(p[i], p[i + 1]);
                if (d != Integer.MIN_VALUE) { end = i + 1; while (end + 1 < p.length && step(p[end], p[end + 1]) == d) end++; }
            }
            i = (end - i + 1 >= 3) ? end + 1 : i + 1;
            count++;
        }
        return count;
    }
    static int nfcNoCfCps(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFC);
        return (int) n.codePoints().filter(c -> Character.getType(c) != Character.FORMAT).count();
    }
    public static void main(String[] a) {
        String[] cases = {
            "😀😀😀😀😀", Normalizer.normalize("ąąąąą", Normalizer.Form.NFD), "1​2​3​4​5​",
            "ñKabcdefgh",            // KELVIN SIGN -> 'K' (singleton): adds the upper-case pool
            "ñ;abcdefgh",            // GREEK QUESTION MARK -> ';' (singleton): adds the ASCII symbol pool
            "ड़ड़ड़ड़ड़", // DEVANAGARI DDDHA: composition exclusion, 1 unit -> 2 code points
            "̈́̈́̈́̈́̈́", // COMBINING GREEK DIALYTIKA TONOS -> U+0308 U+0301
            "אָאָאָאָאָ", // HEBREW ALEF WITH QAMATS: exclusion -> 2 code points
        };
        for (String c : cases) {
            System.out.printf("%-40s units=%2d nfcNoCfCps=%2d old=%6.1f new=%6.1f %s%n",
                c.codePoints().mapToObj(x -> String.format("%04X", x)).reduce((x, y) -> x + " " + y).get().substring(0, Math.min(38, c.codePoints().mapToObj(x -> String.format("%04X", x)).reduce((x, y) -> x + " " + y).get().length())),
                c.length(), nfcNoCfCps(c), oldBits(c), newBits(c), newBits(c) > oldBits(c) + 1e-9 ? "STRONGER" : "");
        }
    }
}
