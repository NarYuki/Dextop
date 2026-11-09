import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Signs the prebuilt Linux image manifest. Run with a JDK, no dependencies:
 *   java LinuxImageSigner.java keygen <private.pem>           creates the key, prints the public key
 *   java LinuxImageSigner.java sign <private.pem> <payload.json> <manifest.json>
 *   java LinuxImageSigner.java verify <public-base64> <manifest.json>
 *
 * ECDSA P-256 is available on every Android version Dextop supports. The manifest is the
 * envelope {"payload": base64(payload bytes), "signature": base64(DER signature)}, so the
 * signed bytes never depend on how JSON is formatted.
 */
public final class LinuxImageSigner {
    private static final String ALGORITHM = "SHA256withECDSA";

    public static void main(String[] args) throws Exception {
        switch (args.length > 0 ? args[0] : "") {
            case "keygen" -> keygen(Path.of(args[1]));
            case "sign" -> sign(Path.of(args[1]), Path.of(args[2]), Path.of(args[3]));
            case "verify" -> verify(args[1], Path.of(args[2]));
            default -> throw new IllegalArgumentException("keygen | sign | verify");
        }
    }

    private static void keygen(Path target) throws Exception {
        if (Files.exists(target)) throw new IllegalStateException("Refusing to replace " + target);
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        var pair = generator.generateKeyPair();
        String encoded = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(pair.getPrivate().getEncoded());
        Files.createFile(target, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        Files.writeString(target, "-----BEGIN PRIVATE KEY-----\n" + encoded + "\n-----END PRIVATE KEY-----\n");
        System.out.println(Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
    }

    private static PrivateKey privateKey(Path file) throws Exception {
        String body = Files.readString(file).replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
        PrivateKey key = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(body)));
        if (!(key instanceof ECPrivateKey)) throw new IllegalArgumentException("Not an EC key");
        return key;
    }

    private static void sign(Path keyFile, Path payloadFile, Path output) throws Exception {
        byte[] payload = Files.readAllBytes(payloadFile);
        Signature signature = Signature.getInstance(ALGORITHM);
        signature.initSign(privateKey(keyFile));
        signature.update(payload);
        Files.writeString(output, "{\"payload\":\"" + Base64.getEncoder().encodeToString(payload)
                + "\",\"signature\":\"" + Base64.getEncoder().encodeToString(signature.sign()) + "\"}\n");
    }

    private static void verify(String publicKey, Path manifest) throws Exception {
        String text = Files.readString(manifest);
        byte[] payload = Base64.getDecoder().decode(field(text, "payload"));
        PublicKey key = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKey)));
        Signature signature = Signature.getInstance(ALGORITHM);
        signature.initVerify(key);
        signature.update(payload);
        if (!signature.verify(Base64.getDecoder().decode(field(text, "signature")))) throw new SecurityException("Bad signature");
        System.out.println(new String(payload, StandardCharsets.UTF_8));
    }

    private static String field(String json, String name) {
        var matcher = java.util.regex.Pattern.compile("\"" + name + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        if (!matcher.find()) throw new IllegalArgumentException("Missing " + name);
        return matcher.group(1);
    }
}
