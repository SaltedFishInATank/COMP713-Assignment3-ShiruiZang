package nz.ac.aut.comp713.config;
import java.security.*;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
/** Java SE crypto uses javax.crypto; all Jakarta EE imports use jakarta.*. */
public final class Passwords {
 private Passwords() {}
 public static String hash(String password) {
  byte[] salt=new byte[16]; new SecureRandom().nextBytes(salt);
  return Base64.getEncoder().encodeToString(salt)+":"+Base64.getEncoder().encodeToString(derive(password,salt));
 }
 public static boolean matches(String password,String encoded) {
  String[] parts=encoded.split(":");
  return MessageDigest.isEqual(derive(password,Base64.getDecoder().decode(parts[0])),Base64.getDecoder().decode(parts[1]));
 }
 private static byte[] derive(String password,byte[] salt) {
  PBEKeySpec key=new PBEKeySpec(password.toCharArray(),salt,120000,256);
  try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(key).getEncoded(); }
  catch(GeneralSecurityException e){ throw new IllegalStateException(e); }
  finally { key.clearPassword(); }
 }
 public static String tokenHash(String value) {
  try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
  catch(GeneralSecurityException e){ throw new IllegalStateException(e); }
 }
}
