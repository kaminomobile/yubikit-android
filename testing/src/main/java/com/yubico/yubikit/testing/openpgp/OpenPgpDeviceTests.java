/*
 * Copyright (C) 2023 Yubico.
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

package com.yubico.yubikit.testing.openpgp;

import com.yubico.yubikit.core.application.InvalidPinException;
import com.yubico.yubikit.core.keys.PrivateKeyValues;
import com.yubico.yubikit.core.keys.PublicKeyValues;
import com.yubico.yubikit.core.smartcard.ApduException;
import com.yubico.yubikit.core.smartcard.SW;
import com.yubico.yubikit.openpgp.ExtendedCapabilityFlag;
import com.yubico.yubikit.openpgp.Kdf;
import com.yubico.yubikit.openpgp.KeyRef;
import com.yubico.yubikit.openpgp.OpenPgpCurve;
import com.yubico.yubikit.openpgp.OpenPgpSession;
import com.yubico.yubikit.openpgp.PinPolicy;
import com.yubico.yubikit.openpgp.Pw;
import com.yubico.yubikit.openpgp.Uif;

import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Assert;
import org.junit.Assume;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;

public class OpenPgpDeviceTests {
    private static final String DEFAULT_PIN = Pw.DEFAULT_USER_PIN;
    private static final String DEFAULT_ADMIN = Pw.DEFAULT_ADMIN_PIN;
    private static final String CHANGED_PIN = "12341234";
    private static final String RESET_CODE = "43214321";
    private static final Logger logger = LoggerFactory.getLogger(OpenPgpDeviceTests.class);

    private static final List<OpenPgpCurve> ecdsaCurves = Stream.of(OpenPgpCurve.values())
            .filter(curve -> !Arrays.asList(OpenPgpCurve.Ed25519, OpenPgpCurve.X25519).contains(curve))
            .collect(Collectors.toList());

    private static int[] getSupportedRsaKeySizes(OpenPgpSession openpgp) {
        return openpgp.supports(OpenPgpSession.FEATURE_RSA4096_KEYS) ? new int[]{2048, 3072, 4096} : new int[]{2048};
    }

    public static void testGenerateRequiresAdmin(OpenPgpSession openpgp) throws Exception {
        try {
            openpgp.generateRsaKey(KeyRef.DEC, 2048);
            Assert.fail();
        } catch (ApduException e) {
            Assert.assertEquals(SW.SECURITY_CONDITION_NOT_SATISFIED, e.getSw());
        }
    }

    public static void testChangePin(OpenPgpSession openpgp) throws Exception {
        openpgp.verifyUserPin(DEFAULT_PIN, false);
        Assert.assertThrows(InvalidPinException.class, () -> openpgp.verifyUserPin(CHANGED_PIN, false));
        Assert.assertThrows(InvalidPinException.class, () -> openpgp.changeUserPin(CHANGED_PIN, DEFAULT_PIN));

        openpgp.changeUserPin(DEFAULT_PIN, CHANGED_PIN);
        openpgp.verifyUserPin(CHANGED_PIN, false);
        openpgp.changeUserPin(CHANGED_PIN, DEFAULT_PIN);
        openpgp.verifyUserPin(DEFAULT_PIN, false);
    }

    public static void testResetPin(OpenPgpSession openpgp) throws Exception {
        int remaining = openpgp.getPinStatus().getAttemptsUser();
        for (int i = remaining; i > 0; i--) {
            try {
                openpgp.verifyUserPin(CHANGED_PIN, false);
                Assert.fail();
            } catch (InvalidPinException e) {
                Assert.assertEquals(e.getAttemptsRemaining(), i - 1);
            }
        }
        assert openpgp.getPinStatus().getAttemptsUser() == 0;

        try {
            openpgp.resetPin(DEFAULT_PIN, null);
            Assert.fail();
        } catch (ApduException e) {
            Assert.assertEquals(e.getSw(), SW.SECURITY_CONDITION_NOT_SATISFIED);
        }

        // Reset PIN using Admin PIN
        openpgp.verifyAdminPin(DEFAULT_ADMIN);
        openpgp.resetPin(DEFAULT_PIN, null);
        remaining = openpgp.getPinStatus().getAttemptsUser();
        assert remaining > 0;
        for (int i = remaining; i > 0; i--) {
            try {
                openpgp.verifyUserPin(CHANGED_PIN, false);
                Assert.fail();
            } catch (InvalidPinException e) {
                Assert.assertEquals(e.getAttemptsRemaining(), i - 1);
            }
        }
        assert openpgp.getPinStatus().getAttemptsUser() == 0;

        // Reset PIN using Reset Code
        openpgp.setResetCode(RESET_CODE);
        Assert.assertThrows(InvalidPinException.class, () -> openpgp.resetPin(DEFAULT_PIN, CHANGED_PIN));
        openpgp.resetPin(DEFAULT_PIN, RESET_CODE);
        assert openpgp.getPinStatus().getAttemptsUser() > 0;
    }

    public static void testSetPinAttempts(OpenPgpSession openpgp) throws Exception {
        Assume.assumeTrue("RSA key generation", openpgp.supports(OpenPgpSession.FEATURE_PIN_ATTEMPTS));

        openpgp.verifyAdminPin(DEFAULT_ADMIN);
        openpgp.setPinAttempts(6, 3, 3);
        assert openpgp.getPinStatus().getAttemptsUser() == 6;

        try {
            openpgp.verifyUserPin(CHANGED_PIN, false);
        } catch (InvalidPinException e) {
            // Ignore
        }
        assert openpgp.getPinStatus().getAttemptsUser() == 5;

        openpgp.setPinAttempts(3, 3, 3);
        assert openpgp.getPinStatus().getAttemptsUser() == 3;
    }

    public static void testGenerateRsaKeys(OpenPgpSession openpgp) throws Exception {
        Assume.assumeTrue("RSA key generation", openpgp.supports(OpenPgpSession.FEATURE_RSA_GENERATION));

        openpgp.verifyAdminPin(DEFAULT_ADMIN);

        byte[] message = "hello".getBytes(StandardCharsets.UTF_8);
        for (int keySize : getSupportedRsaKeySizes(openpgp)) {
            logger.info("RSA key size: {}", keySize);
            PublicKey publicKey = openpgp.generateRsaKey(KeyRef.SIG, keySize).toPublicKey();
            openpgp.verifyUserPin(DEFAULT_PIN, false);
            byte[] signature = openpgp.sign(message);
            Signature verifier = Signature.getInstance("NONEwithRSA");
            verifier.initVerify(publicKey);
            verifier.update(message);
            assert verifier.verify(signature);

            publicKey = openpgp.generateRsaKey(KeyRef.DEC, keySize).toPublicKey();
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] cipherText = cipher.doFinal(message);

            openpgp.verifyUserPin(DEFAULT_PIN, true);
            byte[] decrypted = openpgp.decrypt(cipherText);
            Assert.assertArrayEquals(message, decrypted);
        }
    }

    public static void testGenerateEcKeys(OpenPgpSession openpgp) throws Exception {
        Assume.assumeTrue("EC support", openpgp.supports(OpenPgpSession.FEATURE_EC_KEYS));

        Security.removeProvider("BC");
        Security.insertProviderAt(new BouncyCastleProvider(), 1);
        openpgp.verifyAdminPin(DEFAULT_ADMIN);

        byte[] message = "hello".getBytes(StandardCharsets.UTF_8);
        for (OpenPgpCurve curve : ecdsaCurves) {
            logger.info("Curve: {}", curve);
            PublicKey publicKey = openpgp.generateEcKey(KeyRef.SIG, curve).toPublicKey();
            openpgp.verifyUserPin(DEFAULT_PIN, false);
            byte[] signature = openpgp.sign(message);

            Signature verifier = Signature.getInstance("NONEwithECDSA");
            verifier.initVerify(publicKey);
            verifier.update(message);
            assert verifier.verify(signature);

            publicKey = openpgp.generateEcKey(KeyRef.DEC, curve).toPublicKey();
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("ECDH");
            kpg.initialize(new ECGenParameterSpec(curve.name()));
            KeyPair pair = kpg.generateKeyPair();

            openpgp.verifyUserPin(DEFAULT_PIN, true);
            byte[] actual = openpgp.decrypt(PublicKeyValues.fromPublicKey(pair.getPublic()));
            KeyAgreement ka = KeyAgreement.getInstance("ECDH");
            ka.init(pair.getPrivate());
            ka.doPhase(publicKey, true);
            byte[] expected = ka.generateSecret();
            Assert.assertArrayEquals(expected, actual);
        }
    }

    public static void testGenerateEd25519(OpenPgpSession openpgp) throws Exception {
        Assume.assumeTrue("EC support", openpgp.supports(OpenPgpSession.FEATURE_EC_KEYS));

        Security.removeProvider("BC");
        Security.insertProviderAt(new BouncyCastleProvider(), 1);
        openpgp.verifyAdminPin(DEFAULT_ADMIN);

        byte[] message = "hello".getBytes(StandardCharsets.UTF_8);
        PublicKey publicKey = openpgp.generateEcKey(KeyRef.SIG, OpenPgpCurve.Ed25519).toPublicKey();
        openpgp.verifyUserPin(DEFAULT_PIN, false);
        byte[] signature = openpgp.sign(message);

        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(publicKey);
        verifier.update(message);
        assert verifier.verify(signature);
    }

    public static void testGenerateX25519(OpenPgpSession openpgp) throws Exception {
        Assume.assumeTrue("EC support", openpgp.supports(OpenPgpSession.FEATURE_EC_KEYS));

        Security.removeProvider("BC");
        Security.insertProviderAt(new BouncyCastleProvider(), 1);
        openpgp.verifyAdminPin(DEFAULT_ADMIN);

        PublicKey publicKey = openpgp.generateEcKey(KeyRef.DEC, OpenPgpCurve.X25519).toPublicKey();
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("X25519");
        KeyPair pair = kpg.generateKeyPair();

        openpgp.verifyUserPin(DEFAULT_PIN, true);
        byte[] actual = openpgp.decrypt(PublicKeyValues.fromPublicKey(pair.getPublic()));
        KeyAgreement ka = KeyAgreement.getInstance("XDH");
        ka.init(pair.getPrivate());
        ka.doPhase(publicKey, true);
        byte[] expected = ka.generateSecret();
        Assert.assertArrayEquals(expected, actual);
    }

    public static void testImportRsaKeys(OpenPgpSession openpgp) throws Exception {
        openpgp.verifyAdminPin(DEFAULT_ADMIN);

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        byte[] message = "hello".getBytes(StandardCharsets.UTF_8);
        for (int keySize : getSupportedRsaKeySizes(openpgp)) {
            logger.info("RSA key size: {}", keySize);
            kpg.initialize(keySize);
            KeyPair pair = kpg.generateKeyPair();
            openpgp.putKey(KeyRef.SIG, PrivateKeyValues.fromPrivateKey(pair.getPrivate()));

            byte[] encoded = openpgp.getPublicKey(KeyRef.SIG).getEncoded();
            Assert.assertArrayEquals(pair.getPublic().getEncoded(), encoded);

            PublicKey publicKey = openpgp.getPublicKey(KeyRef.SIG).toPublicKey();
            openpgp.verifyUserPin(DEFAULT_PIN, false);
            byte[] signature = openpgp.sign(message);

            Signature verifier = Signature.getInstance("NONEwithRSA");
            verifier.initVerify(publicKey);
            verifier.update(message);
            assert verifier.verify(signature);

            openpgp.putKey(KeyRef.DEC, PrivateKeyValues.fromPrivateKey(pair.getPrivate()));
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] cipherText = cipher.doFinal(message);

            openpgp.verifyUserPin(DEFAULT_PIN, true);
            byte[] decrypted = openpgp.decrypt(cipherText);
            Assert.assertArrayEquals(message, decrypted);
        }
    }

    public static void testImportEcDsaKeys(OpenPgpSession openpgp) throws Exception {
        Assume.assumeTrue("EC support", openpgp.supports(OpenPgpSession.FEATURE_EC_KEYS));

        Security.removeProvider("BC");
        Security.insertProviderAt(new BouncyCastleProvider(), 1);

        openpgp.verifyAdminPin(DEFAULT_ADMIN);

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("ECDSA");
        List<OpenPgpCurve> curves = new ArrayList<>(Arrays.asList(OpenPgpCurve.values()));
        curves.remove(OpenPgpCurve.Ed25519);
        curves.remove(OpenPgpCurve.X25519);


        byte[] message = "hello".getBytes(StandardCharsets.UTF_8);
        for (OpenPgpCurve curve : curves) {
            logger.info("Curve: {}", curve);
            kpg.initialize(new ECGenParameterSpec(curve.name()));
            KeyPair pair = kpg.generateKeyPair();
            openpgp.putKey(KeyRef.SIG, PrivateKeyValues.fromPrivateKey(pair.getPrivate()));
            PublicKeyValues values = openpgp.getPublicKey(KeyRef.SIG);
            Assert.assertArrayEquals(pair.getPublic().getEncoded(), values.getEncoded());
            PublicKey publicKey = values.toPublicKey();
            openpgp.verifyUserPin(DEFAULT_PIN, false);
            byte[] signature = openpgp.sign(message);

            Signature verifier = Signature.getInstance("NONEwithECDSA");
            verifier.initVerify(publicKey);
            verifier.update(message);
            assert verifier.verify(signature);

            openpgp.putKey(KeyRef.DEC, PrivateKeyValues.fromPrivateKey(pair.getPrivate()));
            KeyPair pair2 = kpg.generateKeyPair();
            KeyAgreement ka = KeyAgreement.getInstance("ECDH");
            ka.init(pair2.getPrivate());
            ka.doPhase(openpgp.getPublicKey(KeyRef.DEC).toPublicKey(), true);
            byte[] expected = ka.generateSecret();

            openpgp.verifyUserPin(DEFAULT_PIN, true);
            byte[] agreement = openpgp.decrypt(PublicKeyValues.fromPublicKey(pair2.getPublic()));

            Assert.assertArrayEquals(expected, agreement);
        }
    }

    public static void testImportEd25519(OpenPgpSession openpgp) throws Exception {
        Assume.assumeTrue("EC support", openpgp.supports(OpenPgpSession.FEATURE_EC_KEYS));

        Security.removeProvider("BC");
        Security.insertProviderAt(new BouncyCastleProvider(), 1);

        openpgp.verifyAdminPin(DEFAULT_ADMIN);

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair pair = kpg.generateKeyPair();
        openpgp.putKey(KeyRef.SIG, PrivateKeyValues.fromPrivateKey(pair.getPrivate()));

        byte[] message = "hello".getBytes(StandardCharsets.UTF_8);

        openpgp.verifyUserPin(DEFAULT_PIN, false);
        byte[] signature = openpgp.sign(message);

        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(openpgp.getPublicKey(KeyRef.SIG).toPublicKey());
        verifier.update(message);
        assert verifier.verify(signature);

        Assert.assertArrayEquals(pair.getPublic().getEncoded(), openpgp.getPublicKey(KeyRef.SIG).getEncoded());
    }

    public static void testImportX25519(OpenPgpSession openpgp) throws Exception {
        Assume.assumeTrue("EC support", openpgp.supports(OpenPgpSession.FEATURE_EC_KEYS));

        Security.removeProvider("BC");
        Security.insertProviderAt(new BouncyCastleProvider(), 1);

        openpgp.verifyAdminPin(DEFAULT_ADMIN);

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("X25519");
        KeyPair pair = kpg.generateKeyPair();
        openpgp.putKey(KeyRef.DEC, PrivateKeyValues.fromPrivateKey(pair.getPrivate()));

        KeyPair pair2 = kpg.generateKeyPair();

        KeyAgreement ka = KeyAgreement.getInstance("X25519");
        ka.init(pair2.getPrivate());
        ka.doPhase(openpgp.getPublicKey(KeyRef.DEC).toPublicKey(), true);
        byte[] expected = ka.generateSecret();

        openpgp.verifyUserPin(DEFAULT_PIN, true);
        byte[] agreement = openpgp.decrypt(PublicKeyValues.Ec.fromPublicKey(pair2.getPublic()));

        Assert.assertArrayEquals(expected, agreement);
    }

    public static void testAttestation(OpenPgpSession openpgp) throws Exception {
        Assume.assumeTrue("Attestation support", openpgp.supports(OpenPgpSession.FEATURE_ATTESTATION));

        openpgp.verifyAdminPin(DEFAULT_ADMIN);

        PublicKey publicKey = openpgp.generateEcKey(KeyRef.SIG, OpenPgpCurve.SECP256R1).toPublicKey();

        openpgp.verifyUserPin(DEFAULT_PIN, false);
        X509Certificate cert = openpgp.attestKey(KeyRef.SIG);

        Assert.assertEquals(publicKey, cert.getPublicKey());
    }

    public static void testSigPinPolicy(OpenPgpSession openpgp) throws Exception {
        openpgp.verifyAdminPin(DEFAULT_ADMIN);

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        byte[] message = "hello".getBytes(StandardCharsets.UTF_8);
        kpg.initialize(2048);
        KeyPair pair = kpg.generateKeyPair();
        openpgp.putKey(KeyRef.SIG, PrivateKeyValues.fromPrivateKey(pair.getPrivate()));
        Assert.assertEquals(0, openpgp.getSignatureCounter());

        try {
            openpgp.sign(message);
            Assert.fail();
        } catch (ApduException e) {
            Assert.assertEquals(SW.SECURITY_CONDITION_NOT_SATISFIED, e.getSw());
        }

        openpgp.setSignaturePinPolicy(PinPolicy.ALWAYS);
        openpgp.verifyUserPin(DEFAULT_PIN, false);
        openpgp.sign(message);
        Assert.assertEquals(1, openpgp.getSignatureCounter());
        try {
            openpgp.sign(message);
            Assert.fail();
        } catch (ApduException e) {
            Assert.assertEquals(SW.SECURITY_CONDITION_NOT_SATISFIED, e.getSw());
        }
        Assert.assertEquals(1, openpgp.getSignatureCounter());

        openpgp.setSignaturePinPolicy(PinPolicy.ONCE);
        openpgp.verifyUserPin(DEFAULT_PIN, false);
        openpgp.sign(message);
        Assert.assertEquals(2, openpgp.getSignatureCounter());
        openpgp.sign(message);
        Assert.assertEquals(3, openpgp.getSignatureCounter());
    }

    public static void testKdf(OpenPgpSession openpgp) throws Exception {
        Assume.assumeTrue("KDF Support", openpgp.getExtendedCapabilities().getFlags().contains(ExtendedCapabilityFlag.KDF));

        // Test setting KDF without admin PIN verification
        try {
            openpgp.setKdf(new Kdf.None());
            Assert.fail();
        } catch (ApduException e) {
            Assert.assertEquals(SW.SECURITY_CONDITION_NOT_SATISFIED, e.getSw());
        }

        // KDF can only be set in a mostly clean state
        openpgp.reset();

        // Set a non-default PINs to ensure that they reset
        openpgp.changeUserPin(DEFAULT_PIN, CHANGED_PIN);
        openpgp.changeAdminPin(DEFAULT_ADMIN, CHANGED_PIN);

        openpgp.verifyAdminPin(CHANGED_PIN);
        openpgp.setKdf(
                Kdf.IterSaltedS2k.create(Kdf.IterSaltedS2k.HashAlgorithm.SHA256, 0x780000)
        );
        openpgp.verifyUserPin(DEFAULT_PIN, false);
        openpgp.verifyAdminPin(DEFAULT_ADMIN);

        openpgp.changeUserPin(DEFAULT_PIN, CHANGED_PIN);
        openpgp.verifyUserPin(CHANGED_PIN, false);
        openpgp.changeAdminPin(DEFAULT_ADMIN, CHANGED_PIN);
        openpgp.verifyAdminPin(CHANGED_PIN);

        openpgp.setKdf(new Kdf.None());
        openpgp.verifyAdminPin(DEFAULT_ADMIN);
        openpgp.verifyUserPin(DEFAULT_PIN, false);
    }

    public static void testUnverifyPin(OpenPgpSession openpgp) throws Exception {
        Assume.assumeTrue("Unverify PIN Support", openpgp.supports(OpenPgpSession.FEATURE_UNVERIFY_PIN));

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair pair = kpg.generateKeyPair();

        openpgp.verifyAdminPin(DEFAULT_ADMIN);
        openpgp.putKey(KeyRef.SIG, PrivateKeyValues.fromPrivateKey(pair.getPrivate()));
        openpgp.setSignaturePinPolicy(PinPolicy.ONCE);

        openpgp.unverifyAdminPin();
        // Test import key after unverify
        try {
            openpgp.putKey(KeyRef.AUT, PrivateKeyValues.fromPrivateKey(pair.getPrivate()));
            Assert.fail();
        } catch (ApduException e) {
            Assert.assertEquals(SW.SECURITY_CONDITION_NOT_SATISFIED, e.getSw());
        }

        openpgp.verifyUserPin(DEFAULT_PIN, false);
        byte[] message = "hello".getBytes(StandardCharsets.UTF_8);
        openpgp.sign(message);

        openpgp.unverifyUserPin();
        // Test sign after unverify
        try {
            openpgp.sign(message);
            Assert.fail();
        } catch (ApduException e) {
            Assert.assertEquals(SW.SECURITY_CONDITION_NOT_SATISFIED, e.getSw());
        }
    }

    public static void testDeleteKey(OpenPgpSession openpgp) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair pair = kpg.generateKeyPair();

        openpgp.verifyAdminPin(DEFAULT_ADMIN);
        openpgp.putKey(KeyRef.SIG, PrivateKeyValues.fromPrivateKey(pair.getPrivate()));

        openpgp.verifyUserPin(DEFAULT_PIN, false);
        byte[] message = "hello".getBytes(StandardCharsets.UTF_8);
        openpgp.sign(message);

        openpgp.deleteKey(KeyRef.SIG);
        try {
            openpgp.sign(message);
            Assert.fail();
        } catch (ApduException e) {
            Assert.assertEquals(SW.CONDITIONS_NOT_SATISFIED, e.getSw());
        }
    }

    public static void testCertificateManagement(OpenPgpSession openpgp) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair pair = kpg.generateKeyPair();

        X500Name name = new X500Name("CN=Example");
        X509v3CertificateBuilder serverCertGen = new X509v3CertificateBuilder(
                name,
                new BigInteger("123456789"),
                new Date(),
                new Date(),
                name,
                SubjectPublicKeyInfo.getInstance(ASN1Sequence.getInstance(pair.getPublic().getEncoded()))
        );
        ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256WithRSA").build(pair.getPrivate());
        X509CertificateHolder holder = serverCertGen.build(contentSigner);

        InputStream stream = new ByteArrayInputStream(holder.getEncoded());
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) cf.generateCertificate(stream);

        openpgp.verifyAdminPin(DEFAULT_ADMIN);
        openpgp.putCertificate(KeyRef.SIG, cert);

        X509Certificate actual = openpgp.getCertificate(KeyRef.SIG);
        Assert.assertNotNull(actual);
        Assert.assertArrayEquals(cert.getEncoded(), actual.getEncoded());

        openpgp.deleteCertificate(KeyRef.SIG);
        Assert.assertNull(openpgp.getCertificate(KeyRef.SIG));
    }

    public static void testGetChallenge(OpenPgpSession openpgp) throws Exception {
        Assume.assumeTrue("Get Challenge Support", openpgp.getExtendedCapabilities().getFlags().contains(ExtendedCapabilityFlag.GET_CHALLENGE));

        byte[] challenge = openpgp.getChallenge(1);
        Assert.assertEquals(1, challenge.length);

        challenge = openpgp.getChallenge(8);
        Assert.assertEquals(8, challenge.length);
        // Make sure it's not all zero
        Assert.assertNotEquals(Hex.toHexString(new byte[8]), Hex.toHexString(challenge));
        // Make sure it changes
        Assert.assertNotEquals(Hex.toHexString(openpgp.getChallenge(8)), Hex.toHexString(challenge));

        challenge = openpgp.getChallenge(255);
        Assert.assertEquals(255, challenge.length);
    }

    public static void testSetUif(OpenPgpSession openpgp) throws Exception {
        Assume.assumeTrue("UIF Support", openpgp.supports(OpenPgpSession.FEATURE_UIF));

        try {
            openpgp.setUif(KeyRef.SIG, Uif.ON);
            Assert.fail();
        } catch (ApduException e) {
            Assert.assertEquals(SW.SECURITY_CONDITION_NOT_SATISFIED, e.getSw());
        }

        openpgp.verifyAdminPin(DEFAULT_ADMIN);
        openpgp.setUif(KeyRef.SIG, Uif.ON);
        Assert.assertEquals(Uif.ON, openpgp.getUif(KeyRef.SIG));

        openpgp.setUif(KeyRef.SIG, Uif.OFF);
        Assert.assertEquals(Uif.OFF, openpgp.getUif(KeyRef.SIG));

        openpgp.setUif(KeyRef.SIG, Uif.FIXED);
        Assert.assertThrows(IllegalStateException.class, () -> openpgp.setUif(KeyRef.SIG, Uif.OFF));

        // Reset to remove FIXED UIF.
        openpgp.reset();
    }
}
