/*
 * Copyright (C) 2020-2023 Yubico.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.yubico.yubikit.testing.piv;

import com.yubico.yubikit.core.internal.codec.Base64;
import com.yubico.yubikit.piv.KeyType;

import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.Assert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Date;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;

@SuppressWarnings("SpellCheckingInspection")
public class PivTestUtils {

    private static final Logger logger = LoggerFactory.getLogger(PivTestUtils.class);

    private enum StaticKey {
        RSA1024(
                KeyType.RSA1024,
                "MIICdQIBADANBgkqhkiG9w0BAQEFAASCAl8wggJbAgEAAoGBALWeZ0E5O2l/iHfc" +
                        "k9mokf1iWH2eZDWQoJoQKUOAeVoKUecNp250J5tL3EHONqWoF6VLO+B+6jTET4Iz" +
                        "97BeUj7gOJHmEw+nqFfguTVmNeeiZ711TNYNpF7kwW7yWghWG+Q7iQEoMXfY3x4B" +
                        "L33H2gKRWtMHK66GJViL1l9s3qDXAgMBAAECgYBO753pFzrfS3LAxbns6/snqcrU" +
                        "LjdXoJhs3YFRuVEE9V9LkP+oXguoz3vXjgzqSvib+ur3U7HvZTM5X+TTXutXdQ5C" +
                        "yORLLtXEZcyCKQI9ihH5fSNJRWRbJ3xe+xi5NANRkRDkro7tm4a5ZD4PYvO4r29y" +
                        "VB5PXlMkOTLoxNSwwQJBAN5lW93Agi9Ge5B2+B2EnKSlUvj0+jJBkHYAFTiHyTZV" +
                        "Ej6baeHBvJklhVczpWvTXb6Nr8cjAKVshFbdQoBwHmkCQQDRD7djZGIWH1Lz0rkL" +
                        "01nDj4z4QYMgUs3AQhnrXPBjEgNzphtJ2u7QrCSOBQQHlmAPBDJ/MTxFJMzDIJGD" +
                        "A10/AkATJjEZz/ilr3D2SHgmuoNuXdneG+HrL+ALeQhavL5jkkGm6GTejnr5yNRJ" +
                        "ZOYKecGppbOL9wSYOdbPT+/o9T55AkATXCY6cRBYRhxTcf8q5i6Y2pFOaBqxgpmF" +
                        "JVnrHtcwBXoGWqqKQ1j8QAS+lh5SaY2JtnTKrI+NQ6Qmqbxv6n7XAkBkhLO7pplI" +
                        "nVh2WjqXOV4ZAoOAAJlfpG5+z6mWzCZ9+286OJQLr6OVVQMcYExUO9yVocZQX+4X" +
                        "qEIF0qAB7m31",
                "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQC1nmdBOTtpf4h33JPZqJH9Ylh9" +
                        "nmQ1kKCaEClDgHlaClHnDadudCebS9xBzjalqBelSzvgfuo0xE+CM/ewXlI+4DiR" +
                        "5hMPp6hX4Lk1ZjXnome9dUzWDaRe5MFu8loIVhvkO4kBKDF32N8eAS99x9oCkVrT" +
                        "ByuuhiVYi9ZfbN6g1wIDAQAB"
        ),
        RSA2048(
                KeyType.RSA2048,
                "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC0G266KNssenUQ" +
                        "wsqN3+f3ysmiHgp4345wsaiDcxXryXX3pXr3vYdiJFQ6HiiMbfdpm4FeulLYCOdB" +
                        "ghKHIh/MnxTuwq6mPrxzLFxqGfHinvORc4Y+mZSiicN/Ajo+uQdgH5LrhlHJ0g7a" +
                        "e26RWW3Z4pOel/SeXWJgKm4prhKzi6Or3NZ1l4Wpg4C/lrLD9/bhL6XdUmr/kXc2" +
                        "UoldUz1ZyTNmDqr0oyix52jX+Tpxp7WsPUmXUoapxVpugOQKlkCGFltb5jnaK8VY" +
                        "rlBfN0a7N0o+HCSIThjBLbr65qKXOmUYgS+q5OmidyeCz/1AJ5OLwSf63M71NXMt" +
                        "ZoJjLdMBAgMBAAECggEAT6Z+HnfpDc+OK/5pQ7sMxCn7Z+WvLet3++ClrJRd0mvC" +
                        "7uVQ73TzBXUZhqZFumz7aMnrua/e6UlutCrI9NgjhgOoZzrTsBO4lZq9t/KHZXh0" +
                        "MRQM/2w+Lm+MdIPQrGJ5n4n3GI/LZdyu0vKZYFBTY3NvY0jCVrLnya2aEHa6MIpH" +
                        "sDyJa0EpjZRMHscPAP4C9h0EE/kXdFuu8Q4I+RUhnWAEAox9wGq05cbWAnzz6f5W" +
                        "WWHUL2CfPvSLHx7jjCXOmXf035pj91IfHghVoQyU0UW29xKSqfJv7nJwqV67C0cb" +
                        "kd2MeNARiFi7z4kp6ziLU6gPeLQq3iyWy35hTYPl3QKBgQDdlznGc4YkeomH3W22" +
                        "nHol3BUL96gOrBSZnziNM19hvKQLkRhyIlikQaS7RWlzKbKtDTFhPDixWhKEHDWZ" +
                        "1DRs9th8LLZHXMP+oUyJPkFCX28syP7D4cpXNMbRk5yJXcuF72sYMs4dldjUQVa2" +
                        "9DaEDkaVFOEAdIVOPNmvmE7MDwKBgQDQEyImwRkHzpp+IAFqhy06DJpmlnOlkD0A" +
                        "hrDAT+EpXTwJssZK8DHcwMhEQbBt+3jXjIXLdko0bR9UUKIpviyF3TZg7IGlMCT4" +
                        "XSs/UlWUct2n9QRrIV5ivRN5+tZZr4+mxbm5d7aa73oQuZl70d5mn6P4y5OsEc5s" +
                        "XFNwUSCf7wKBgDo5NhES4bhMCj8My3sj+mRgQ5d1Z08ToAYNdAqF6RYBPwlbApVa" +
                        "uPfP17ztLBv6ZNxbjxIBhNP02tCjqOHWhD/tTEy0YuC1WzpYn4egN/18nfWiim5l" +
                        "sYjgcS04H/VoE8YJdpZRIx9a9DIxSNuhp4FjTuB1L/mypCQ+kOQ2nN25AoGBAJlw" +
                        "0qlzkorQT9ucrI6rWq3JJ39piaTZRjMCIIvhHDENwT2BqXsPwCWDwOuc6Ydhf86s" +
                        "oOnWtIgOxKC/yaYwyNJ6vCQjpMN1Sn4g7siGZffP8Sdvpy99bwYvWpKEaNfAgJXC" +
                        "j+B2qKF+4iw9QjMuI+zX4uqQ7bhhdTExsJJOMVnfAoGABSbxwvLPglJ6cpoqyGL5" +
                        "Ihg1LS4qog29HVmnX4o/HLXtTCO169yQP5lBWIGRO/yUcgouglJpeikcJSPJROWP" +
                        "Ls4b2aPv5hhSx47MGZbVAIhSbls5zOZXDZm4wdfQE5J+4kAVlYF73ZCrH24Zbqqy" +
                        "MF/0wDt/NExsv6FMUwSKfyY=",
                "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtBtuuijbLHp1EMLKjd/n" +
                        "98rJoh4KeN+OcLGog3MV68l196V6972HYiRUOh4ojG33aZuBXrpS2AjnQYIShyIf" +
                        "zJ8U7sKupj68cyxcahnx4p7zkXOGPpmUoonDfwI6PrkHYB+S64ZRydIO2ntukVlt" +
                        "2eKTnpf0nl1iYCpuKa4Ss4ujq9zWdZeFqYOAv5ayw/f24S+l3VJq/5F3NlKJXVM9" +
                        "WckzZg6q9KMosedo1/k6cae1rD1Jl1KGqcVaboDkCpZAhhZbW+Y52ivFWK5QXzdG" +
                        "uzdKPhwkiE4YwS26+uailzplGIEvquTponcngs/9QCeTi8En+tzO9TVzLWaCYy3T" +
                        "AQIDAQAB"
        ),
        ECCP256(
                KeyType.ECCP256,
                "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgaEygF+BBlaq6MkmJ" +
                        "uN4CTGYo2QPJZYadPjRhKPodCdyhRANCAAQA9NDknDc4Mor6mWKaW0zo3BLSwF8d" +
                        "1yNf4HCLn/zbwvEkjuXo7+tob8faiZrixXoK7zuxip8yh86r+f0x1bFG",
                "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEAPTQ5Jw3ODKK+plimltM6NwS0sBf" +
                        "HdcjX+Bwi5/828LxJI7l6O/raG/H2oma4sV6Cu87sYqfMofOq/n9MdWxRg=="
        ),
        ECCP384(
                KeyType.ECCP384,
                "MIG2AgEAMBAGByqGSM49AgEGBSuBBAAiBIGeMIGbAgEBBDCqCSz+IHpchR9ffO4T" +
                        "JKkxNiBg5Wlg2AK7u4ge/egQZC/qQdTxFZZp8wTHDMNzeaOhZANiAAQ9p9ePq4YY" +
                        "/MfPRQUfx/OPxi1Ch6e4uIhgVYRUJYgW/kfZhyGRqlEnxXxbdBiCigPDHTWg0bot" +
                        "pzmhGWfAmQ63v/2gluvB1sepqojTTzKlvkGLYui/UZR0GVzyM1KSMww=",
                "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEPafXj6uGGPzHz0UFH8fzj8YtQoenuLiI" +
                        "YFWEVCWIFv5H2YchkapRJ8V8W3QYgooDwx01oNG6Lac5oRlnwJkOt7/9oJbrwdbH" +
                        "qaqI008ypb5Bi2Lov1GUdBlc8jNSkjMM"
        );

        private final KeyType keyType;
        private final String privateKey;
        private final String publicKey;

        StaticKey(KeyType keyType, String privateKey, String publicKey) {
            this.keyType = keyType;
            this.privateKey = privateKey;
            this.publicKey = publicKey;
        }

        private KeyPair getKeyPair() {
            try {
                KeyFactory kf = KeyFactory.getInstance(keyType.params.algorithm.name());
                return new KeyPair(
                        kf.generatePublic(new X509EncodedKeySpec(Base64.decode(publicKey))),
                        kf.generatePrivate(new PKCS8EncodedKeySpec(Base64.decode(privateKey)))
                );
            } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static final String[] EC_SIGNATURE_ALGORITHMS = new String[]{"NONEwithECDSA", "SHA1withECDSA", "SHA224withECDSA", "SHA256withECDSA", "SHA384withECDSA", "SHA512withECDSA"};
    private static final String[] RSA_SIGNATURE_ALGORITHMS = new String[]{"NONEwithRSA", "MD5withRSA", "SHA1withRSA", "SHA224withRSA", "SHA256withRSA", "SHA384withRSA", "SHA512withRSA"};
    private static final String[] RSA_CIPHER_ALGORITHMS = new String[]{"RSA/ECB/PKCS1Padding", "RSA/ECB/OAEPWithSHA-1AndMGF1Padding", "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"};

    public static KeyPair generateKey(KeyType keyType) {
        switch (keyType) {
            case ECCP256:
                return generateEcKey("secp256r1");
            case ECCP384:
                return generateEcKey("secp384r1");
            case RSA1024:
            case RSA2048:
                return generateRsaKey(keyType.params.bitLength);
        }
        throw new IllegalArgumentException("Invalid algorithm");
    }

    private static KeyPair generateEcKey(String curve) {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(KeyType.Algorithm.EC.name());
            kpg.initialize(new ECGenParameterSpec(curve), new SecureRandom());
            return kpg.generateKeyPair();
        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
            throw new IllegalStateException(e);
        }
    }

    private static KeyPair generateRsaKey(int keySize) {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(KeyType.Algorithm.RSA.name());
            kpg.initialize(keySize);
            return kpg.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static KeyPair loadKey(KeyType keyType) {
        for (StaticKey staticKey : StaticKey.values()) {
            if (keyType == staticKey.keyType) {
                return staticKey.getKeyPair();
            }
        }
        throw new IllegalArgumentException("Unknown algorithm");
    }

    public static X509Certificate createCertificate(KeyPair keyPair) throws IOException, CertificateException {
        X500Name name = new X500Name("CN=Example");
        X509v3CertificateBuilder serverCertGen = new X509v3CertificateBuilder(
                name,
                new BigInteger("123456789"),
                new Date(),
                new Date(),
                name,
                SubjectPublicKeyInfo.getInstance(ASN1Sequence.getInstance(keyPair.getPublic().getEncoded()))
        );

        String algorithm;
        KeyType keyType = KeyType.fromKey(keyPair.getPrivate());
        switch (keyType.params.algorithm) {
            case EC:
                algorithm = "SHA256WithECDSA";
                break;
            case RSA:
                algorithm = "SHA256WithRSA";
                break;
            default:
                throw new IllegalStateException();
        }
        try {
            ContentSigner contentSigner = new JcaContentSignerBuilder(algorithm).build(keyPair.getPrivate());
            X509CertificateHolder holder = serverCertGen.build(contentSigner);

            InputStream stream = new ByteArrayInputStream(holder.getEncoded());
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(stream);
        } catch (OperatorCreationException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] sign(PrivateKey privateKey, Signature algorithm) throws Exception {
        byte[] message = "Hello world".getBytes(StandardCharsets.UTF_8);
        algorithm.initSign(privateKey);
        algorithm.update(message);
        return algorithm.sign();
    }

    public static void verify(PublicKey publicKey, Signature algorithm, byte[] signature) throws Exception {
        byte[] message = "Hello world".getBytes(StandardCharsets.UTF_8);
        algorithm.initVerify(publicKey);
        algorithm.update(message);
        boolean result = algorithm.verify(signature);
        Assert.assertTrue("Signature mismatch for " + algorithm.getAlgorithm(), result);
    }

    public static void rsaSignAndVerify(PrivateKey privateKey, PublicKey publicKey) throws Exception {
        for (String algorithm : RSA_SIGNATURE_ALGORITHMS) {
            verify(publicKey, Signature.getInstance(algorithm), sign(privateKey, Signature.getInstance(algorithm)));
        }
    }

    public static void encryptAndDecrypt(PrivateKey privateKey, PublicKey publicKey, Cipher algorithm) throws Exception {
        byte[] message = "Hello world".getBytes(StandardCharsets.UTF_8);

        algorithm.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] encrypted = algorithm.doFinal(message);

        algorithm = Cipher.getInstance(algorithm.getAlgorithm());
        algorithm.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] decrypted = algorithm.doFinal(encrypted);

        Assert.assertArrayEquals("Decrypted mismatch", decrypted, message);
    }

    public static void rsaEncryptAndDecrypt(PrivateKey privateKey, PublicKey publicKey) throws Exception {
        for (String algorithm : RSA_CIPHER_ALGORITHMS) {
            encryptAndDecrypt(privateKey, publicKey, Cipher.getInstance(algorithm));
        }
    }

    public static void rsaTests() throws Exception {
        for (KeyPair keyPair : new KeyPair[]{generateKey(KeyType.RSA1024), generateKey(KeyType.RSA2048)}) {
            rsaEncryptAndDecrypt(keyPair.getPrivate(), keyPair.getPublic());
            rsaSignAndVerify(keyPair.getPrivate(), keyPair.getPublic());
        }
    }

    public static void ecTests() throws Exception {
        for (KeyPair keyPair : new KeyPair[]{generateKey(KeyType.ECCP256), generateKey(KeyType.ECCP384)}) {
            ecSignAndVerify(keyPair.getPrivate(), keyPair.getPublic());
            ecKeyAgreement(keyPair.getPrivate(), keyPair.getPublic());
        }
    }

    public static void ecSignAndVerify(PrivateKey privateKey, PublicKey publicKey) throws Exception {
        for (String algorithm : EC_SIGNATURE_ALGORITHMS) {
            logger.debug("Test {}", algorithm);
            verify(publicKey, Signature.getInstance(algorithm), sign(privateKey, Signature.getInstance(algorithm)));
        }
    }

    public static void ecKeyAgreement(PrivateKey privateKey, PublicKey publicKey) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(((ECKey) publicKey).getParams());

        KeyPair peerPair = kpg.generateKeyPair();

        KeyAgreement ka = KeyAgreement.getInstance("ECDH");

        ka.init(privateKey);
        ka.doPhase(peerPair.getPublic(), true);
        byte[] secret = ka.generateSecret();

        ka = KeyAgreement.getInstance("ECDH");
        ka.init(peerPair.getPrivate());
        ka.doPhase(publicKey, true);
        byte[] peerSecret = ka.generateSecret();

        Assert.assertArrayEquals("Secret mismatch", secret, peerSecret);
    }
}
