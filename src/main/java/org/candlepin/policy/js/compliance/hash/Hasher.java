/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.policy.js.compliance.hash;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.util.Collection;

/**
 * Generates a an SHA256 hash of objects via respective {@link HashableStringGenerator}s.
 */
public class Hasher {
    private static Logger log = LoggerFactory.getLogger(Hasher.class);
    private StringBuilder sink;

    // Trivia: the second seed is the seventh Mersenne prime
    private static final HashFunction HASH_FUNCTION = Hashing.concatenating(
        Hashing.murmur3_128(),
        Hashing.murmur3_128(524287)
    );

    public Hasher() {
        sink = new StringBuilder();
    }

    /**
     * Produces two concatenated Murmur3 hashes of anything that was put into this hasher to produce a 256
     * bit digest.  We concatenate two 128 bit digests for legacy reasons: previously we used SHA256 which
     * produced a 256 bit digest and I wanted to maintain continuity in the digest size.
     *
     * @return a 256 bit hex string
     */
    public String hash() {
        String data = sink.toString();
        log.debug("Hashing {}", data);
        HashCode c = HASH_FUNCTION.newHasher().putString(data, Charset.defaultCharset()).hash();
        return c.toString();
    }

    /**
     * Adds the specified collection to the result of this hash. The resulting string added to the hash
     * is generated by the specified generator.
     *
     * @see HashableStringGenerators
     *
     * @param toConvert the collection to add
     * @param generator the generator responsible for generating the hash string for each object
     *                  in the collection.
     */
    public <T extends Object> void putCollection(Collection<T> toConvert,
        HashableStringGenerator<T> generator) {
        sink.append(HashableStringGenerators.generateFromCollection(toConvert, generator));
    }

    /**
     * Adds the specified Object to the result of this hash. The string added to the hash will be generated
     * by the specified generator.
     *
     * @see HashableStringGenerators
     *
     * @param toConvert the object to add
     * @param generator the generator responsible for generating the hash string for the object.
     */
    public <T extends Object> void putObject(T toConvert, HashableStringGenerator<T> generator) {
        sink.append(HashableStringGenerators.generateFromObject(toConvert, generator));
    }

}
