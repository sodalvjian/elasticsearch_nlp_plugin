/*
 *  Copyright: (c) 2019 Mayo Foundation for Medical Education and
 *  Research (MFMER). All rights reserved. MAYO, MAYO CLINIC, and the
 *  triple-shield Mayo logo are trademarks and service marks of MFMER.
 *
 *  Except as contained in the copyright notice above, or as used to identify
 *  MFMER as the author of this software, the trade names, trademarks, service
 *  marks, or product names of the copyright holder shall not be used in
 *  advertising, promotion or otherwise in connection with this software without
 *  prior written authorization of the copyright holder.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.ohnlp.elasticsearchnlp.payloads;

import org.apache.lucene.util.BytesRef;
import org.ohnlp.elasticsearchnlp.ElasticsearchNLPPlugin;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;

/**
 * A representation of NLP artifact data with translation to ES payload (byte arrays) format<br/>
 * This object utilizes Java's {@link ByteBuffer} for encoding, and is set to {@link ByteOrder#LITTLE_ENDIAN} for
 * consistency.
 * <br/>
 * <h2>Byte Format Information</h2>
 * <h3>Byte 0: ConText information</h3>
 * b & 0x1 = 0 -> Positive, 1 -> Negated<br/>
 * b & 0x2 = 0 -> Asserted, 1 -> Possible<br/>
 * b & 0x4 = 0 -> Present, 1 -> Historical<br/>
 * b & 0x8 = 0 -> Patient is Subject, 1 -> Other is Subject<br/><br/>
 * <h3>Byte 1: ConText Trigger information</h3>
 * b & 0x1 = 0 -> Not Negation Trigger, 1 -> Negation Trigger<br/>
 * b & 0x2 = 0 -> Not Assertion Trigger, 1 -> Assertion Trigger<br/>
 * b & 0x4 = 0 -> Not Historical Trigger, 1 -> Historical Trigger<br/>
 * b & 0x8 = 0 -> Not Experiencer Trigger, 1 -> Experiencer Trigger<br/><br/>
 */
public class NLPPayload {
    //  Byte 0: ConText information
    private boolean isPositive; // 0x1
    private boolean isAsserted; // 0x2
    private boolean isPresent; // 0x4
    private boolean patientIsSubject; // 0x8

    // Byte 1: Trigger information
    public boolean isNegationTrigger;
    public boolean isAssertionTrigger;
    public boolean isHistoricalTrigger;
    public boolean isExperiencerTrigger;

    private static final byte[] DEFAULT_PARAMS = new byte[2];


    static {
        Arrays.fill(DEFAULT_PARAMS, (byte) 0);
    }

    public NLPPayload() {
        this(DEFAULT_PARAMS);
    }

    public NLPPayload(BytesRef bytes) {
        this(ByteBuffer.wrap(bytes.bytes).order(ByteOrder.LITTLE_ENDIAN), bytes.offset);
    }

    public NLPPayload(byte[] source) {
        this(ByteBuffer.wrap(source).order(ByteOrder.LITTLE_ENDIAN), 0);
    }

    public NLPPayload(ByteBuffer buff, int offset) {
        // Status
        byte contextByte = buff.get(offset);
        isPositive = (contextByte & 0x1) == 0;
        isAsserted = (contextByte & 0x2) == 0;
        isPresent = (contextByte & 0x4) == 0;
        patientIsSubject = (contextByte & 0x8) == 0;
        // Triggers (for coordination)
        byte triggerByte = buff.get(offset + 1);
        isNegationTrigger = (triggerByte & 0x1) == 1;
        isAssertionTrigger = (triggerByte & 0x2) == 0x2;
        isHistoricalTrigger = (triggerByte & 0x4) == 0x4;
        isExperiencerTrigger = (triggerByte & 0x8) == 0x8;
    }

    /**
     * @return A byte array with the specifications as detailed in the class javadoc
     */
    public byte[] toBytes() {
//        ByteBuffer buff = ByteBuffer.allocate(3 + embeddings.length * EMBEDDING_BYTE_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer buff = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN);
        // ConText Byte
        byte conTextByte = 0b0;
        if (!isPositive) {
            conTextByte = (byte) (conTextByte | 0x1);
        }
        if (!isAsserted) {
            conTextByte = (byte) (conTextByte | 0x2);
        }
        if (!isPresent) {
            conTextByte = (byte) (conTextByte | 0x4);
        }
        if (!patientIsSubject) {
            conTextByte = (byte) (conTextByte | 0x8);
        }
        buff.put(0, conTextByte);
        // Trigger States
        byte triggerByte = 0b0;
        if (isNegationTrigger) {
            triggerByte = (byte) (triggerByte | 0x1);
        }
        if (isAssertionTrigger) {
            triggerByte = (byte) (triggerByte | 0x2);
        }
        if (isHistoricalTrigger) {
            triggerByte = (byte) (triggerByte | 0x4);
        }
        if (isExperiencerTrigger) {
            triggerByte = (byte) (triggerByte | 0x8);
        }
        buff.put(1, triggerByte);
        byte[] ret = new byte[2];
        buff.get(ret);
        return ret;
    }

    /**
     * Determines if the term to which this payload corresponds should be used as part of the query coordination step
     * of scoring
     *
     * @return True if payload is not a negation, assertion, or historical trigger (note that it can be an experiencer
     * trigger, as the specific subject may be relevant to the query)
     */
    public boolean isQueryTerm() {
        return !(isNegationTrigger() || isAssertionTrigger() || isHistoricalTrigger());
    }


    public boolean isPositive() {
        return isPositive;
    }

    public void setPositive(boolean positive) {
        isPositive = positive;
    }

    public boolean isAsserted() {
        return isAsserted;
    }

    public void setAsserted(boolean asserted) {
        isAsserted = asserted;
    }

    public boolean isPresent() {
        return isPresent;
    }

    public void setPresent(boolean present) {
        isPresent = present;
    }

    public boolean patientIsSubject() {
        return patientIsSubject;
    }

    public void setPatientIsSubject(boolean patientIsSubject) {
        this.patientIsSubject = patientIsSubject;
    }

    public boolean isPatientIsSubject() {
        return patientIsSubject;
    }

    public boolean isNegationTrigger() {
        return isNegationTrigger;
    }

    public void setNegationTrigger(boolean negationTrigger) {
        isNegationTrigger = negationTrigger;
    }

    public boolean isAssertionTrigger() {
        return isAssertionTrigger;
    }

    public void setAssertionTrigger(boolean assertionTrigger) {
        isAssertionTrigger = assertionTrigger;
    }

    public boolean isHistoricalTrigger() {
        return isHistoricalTrigger;
    }

    public void setHistoricalTrigger(boolean historicalTrigger) {
        isHistoricalTrigger = historicalTrigger;
    }

    public boolean isExperiencerTrigger() {
        return isExperiencerTrigger;
    }

    public void setExperiencerTrigger(boolean experiencerTrigger) {
        isExperiencerTrigger = experiencerTrigger;
    }

    @Override
    public String toString() {
        return "[negated:" + !isPositive
                + "; asserted:" + isAsserted
                + "; historical:" + !isPresent
                + "; experiencer: " + (patientIsSubject ? "patient" : "other");
    }
}
