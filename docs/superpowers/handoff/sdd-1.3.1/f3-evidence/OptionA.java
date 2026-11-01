import java.text.Normalizer;
/** Option A: estimate = min(today, NFC/no-Cf/code points); length = min(UTF-16 units, NFC/no-Cf code points). */
public class OptionA {
    static double bits(String s) { return Math.min(Probe.oldBits(s), Probe.newBits(s)); }
    static int len(String s) { return Math.min(s.length(), Probe.nfcNoCfCps(s)); }
    static String verdict(String s) { return len(s) < 10 ? "MASTER_TOO_SHORT" : bits(s) < 50 ? "MASTER_TOO_WEAK" : "passes length+estimate"; }
    static String literal(String s) { return Probe.nfcNoCfCps(s) < 10 ? "MASTER_TOO_SHORT" : Probe.newBits(s) < 50 ? "MASTER_TOO_WEAK" : "passes length+estimate"; }
    static String today(String s) { return s.length() < 10 ? "MASTER_TOO_SHORT" : Probe.oldBits(s) < 50 ? "MASTER_TOO_WEAK" : "passes length+estimate"; }
    public static void main(String[] a) {
        String[] c = { "😀😀😀😀😀", Normalizer.normalize("ąąąąą", Normalizer.Form.NFD), "1​2​3​4​5​",
            "ड़ड़ड़ड़ड़", "Ábaco12345", "Xq7#vB2!mK", "casa perro luz mar sol", "contraseña123" };
        String[] n = { "5 emojis", "ąąąąą NFD", "12345 + U+200B", "ड़ x5 (U+095C)", "A+U+0301 baco12345", "Xq7#vB2!mK", "casa perro luz mar sol", "contraseña123" };
        System.out.printf("%-24s | %-22s | %-22s | %-22s%n", "candidate", "today", "literal N-02", "option A");
        for (int i = 0; i < c.length; i++) System.out.printf("%-24s | %-22s | %-22s | %-22s%n", n[i], today(c[i]), literal(c[i]), verdict(c[i]));
    }
}
