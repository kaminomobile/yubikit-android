/*
 * Copyright (C) 2022-2023 Yubico.
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

package com.yubico.yubikit.testing.fido;

import static com.yubico.yubikit.testing.fido.Ctap2ClientPinInstrumentedTests.supportsPinUvAuthProtocol;

import androidx.test.filters.LargeTest;

import com.yubico.yubikit.fido.ctap.PinUvAuthProtocol;
import com.yubico.yubikit.fido.ctap.PinUvAuthProtocolV1;
import com.yubico.yubikit.fido.ctap.PinUvAuthProtocolV2;
import com.yubico.yubikit.testing.framework.FidoInstrumentedTests;

import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

@RunWith(Enclosed.class)
public class BasicWebAuthnClientInstrumentedTests {
    @LargeTest
    @RunWith(Parameterized.class)
    public static class BasicWebAuthnClientParametrizedTests extends FidoInstrumentedTests {

        @Parameterized.Parameter
        public PinUvAuthProtocol pinUvAuthProtocol;

        @Parameterized.Parameters
        public static Collection<PinUvAuthProtocol> data() {
            return Arrays.asList(
                    new PinUvAuthProtocolV1(),
                    new PinUvAuthProtocolV2());
        }

        @Test
        public void testMakeCredentialGetAssertion() throws Throwable {
            withCtap2Session(
                    (device, session) -> supportsPinUvAuthProtocol(session, pinUvAuthProtocol),
                    BasicWebAuthnClientTests::testMakeCredentialGetAssertion,
                    pinUvAuthProtocol);
        }

        @Test
        public void testGetAssertionMultipleUsersRk() throws Throwable {
            withCtap2Session(
                    (device, session) -> supportsPinUvAuthProtocol(session, pinUvAuthProtocol),
                    BasicWebAuthnClientTests::testGetAssertionMultipleUsersRk,
                    pinUvAuthProtocol);
        }

        @Test
        public void testGetAssertionWithAllowList() throws Throwable {
            withCtap2Session(
                    (device, session) -> supportsPinUvAuthProtocol(session, pinUvAuthProtocol),
                    BasicWebAuthnClientTests::testGetAssertionWithAllowList,
                    pinUvAuthProtocol);
        }

        @Test
        public void testMakeCredentialWithExcludeList() throws Throwable {
            withCtap2Session(
                    (device, session) -> supportsPinUvAuthProtocol(session, pinUvAuthProtocol),
                    BasicWebAuthnClientTests::testMakeCredentialWithExcludeList,
                    pinUvAuthProtocol);
        }

        @Test
        public void testMakeCredentialKeyAlgorithms() throws Throwable {
            withCtap2Session(
                    (device, session) -> supportsPinUvAuthProtocol(session, pinUvAuthProtocol),
                    BasicWebAuthnClientTests::testMakeCredentialKeyAlgorithms,
                    pinUvAuthProtocol);
        }

        @Test
        public void testClientPinManagement() throws Throwable {
            withCtap2Session(
                    (device, session) -> supportsPinUvAuthProtocol(session, pinUvAuthProtocol),
                    BasicWebAuthnClientTests::testClientPinManagement,
                    pinUvAuthProtocol);
        }

        @Test
        public void testClientCredentialManagement() throws Throwable {
            withCtap2Session(
                    "Credential management or PIN/UV Auth protocol not supported",
                    (device, session) ->
                            Ctap2CredentialManagementInstrumentedTests
                                    .isCredentialManagementSupported(session) &&
                                    supportsPinUvAuthProtocol(session, pinUvAuthProtocol),
                    BasicWebAuthnClientTests::testClientCredentialManagement,
                    pinUvAuthProtocol);
        }
    }
}
