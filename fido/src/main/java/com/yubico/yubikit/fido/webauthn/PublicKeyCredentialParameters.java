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

package com.yubico.yubikit.fido.webauthn;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class PublicKeyCredentialParameters {
    private static final String TYPE = "type";
    private static final String ALG = "alg";

    private final PublicKeyCredentialType type;
    private final int alg;

    public PublicKeyCredentialParameters(PublicKeyCredentialType type, int alg) {
        this.type = type;
        this.alg = alg;
    }

    public PublicKeyCredentialType getType() {
        return type;
    }

    public int getAlg() {
        return alg;
    }

    public Map<String, ?> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put(TYPE, type.toString());
        map.put(ALG, alg);
        return map;
    }

    public static PublicKeyCredentialParameters fromMap(Map<String, ?> map) {
        return new PublicKeyCredentialParameters(
                PublicKeyCredentialType.fromString(Objects.requireNonNull((String) map.get(TYPE))),
                Objects.requireNonNull((Integer) map.get(ALG))
        );
    }
}
