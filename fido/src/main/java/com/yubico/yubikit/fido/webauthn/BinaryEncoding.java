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

package com.yubico.yubikit.fido.webauthn;

import org.apache.commons.codec.binary.Base64;

public enum BinaryEncoding {
    NONE,
    URL_SAFE_BASE64;

    static final BinaryEncoding DEFAULT = URL_SAFE_BASE64;

    public static Object doEncode(byte[] data, BinaryEncoding binaryEncoding) {
        switch (binaryEncoding) {
            case URL_SAFE_BASE64:
                return Base64.encodeBase64URLSafeString(data);
            default:
            case NONE:
                return data;
        }
    }

    public static byte[] doDecode(Object data, BinaryEncoding binaryEncoding) {
        switch (binaryEncoding) {
            case URL_SAFE_BASE64:
                return Base64.decodeBase64((String) data);
            default:
            case NONE:
                return (byte[]) data;
        }
    }
}


