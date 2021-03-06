/*
The OpenTRV project licenses this file to you
under the Apache Licence, Version 2.0 (the "Licence");
you may not use this file except in compliance
with the Licence. You may obtain a copy of the Licence at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the Licence is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied. See the Licence for the
specific language governing permissions and limitations
under the Licence.

Author(s) / Copyright (s): Damon Hart-Davis 2014
*/

package uk.org.opentrv.comms.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**Parsed representation of '@' remote binary stats record in various forms.
 * Should cope with various levels of detail.
 * <p>
 * Has a representation as a String key to String value map for convenience.
 * <p>
 * Immutable, thread-safe.
 */
public final class ParsedRemoteBinaryStatsRecord
    {
    /**Time record was constructed. */
    public final long constructionTime = System.currentTimeMillis();

    /**Non-null, non-empty 'raw' form as received from OpenTRV unit.
     * Example: <tt>@D49;T19C7</tt>.
     */
    public final String raw;

    /**Map (immutable) from key to record section (omitting leading key char); never empty. */
    public final Map<Character, String> sectionsByKey;

    /**Get immutable Map from Character label to String value; never null. */
    public final Map<Character, String> getMapByCharacter() { return(sectionsByKey); }

    /**Cache for getMapByString(); null until first use (then not changed).
     * Accessed under lock on entire record instance.
     */
    private Map<String, Object> getMapByString_cache;

    /**Get immutable Map from String label to String or Number value for convenience, cached; never null. */
    public synchronized final Map<String, Object> getMapByString()
        {
        if(null != getMapByString_cache) { return(getMapByString_cache); }
        final Map<String, Object> result = new HashMap<>(sectionsByKey.size());
        for(final Character key : sectionsByKey.keySet())
            { result.put(Character.toString(key), sectionsByKey.get(key)); }
        getMapByString_cache = Collections.unmodifiableMap(result);
        return(result);
        }

    /**Extracted ID; never null. */
    public final String ID;

    /**Create an instance and parse it, possibly eagerly or lazily. */
    public ParsedRemoteBinaryStatsRecord(final String raw)
        {
        if((null == raw) || (raw.length() < 2) || (CommonSensorLabels.ID.getLabel() != raw.charAt(0))) { throw new IllegalArgumentException(); }
        this.raw = raw;
        final Map<Character, String> s = new HashMap<>();
        for(final String section : raw.split(";"))
            {
            final String st = section.trim();
            if("".equals(st)) { throw new IllegalArgumentException("empty section"); }
            final Character k = Character.valueOf(st.charAt(0));
            if(s.containsKey(k)) { throw new IllegalArgumentException("duplicate section: " + k); }
            s.put(k, st.substring(1));
            }
        sectionsByKey = Collections.unmodifiableMap(s);
        ID = s.get(CommonSensorLabels.ID.getLabel());
        assert(null != ID);
        }


    /**Parse temperature from float of the form ddCh (dd is decimal, h is hex). */
    public static float parseTemperatureFromDDCH(final String rawTempValue)
        {
        final int rtlen = rawTempValue.length();
        final float temp = Integer.parseInt(rawTempValue.substring(0, rtlen-2), 10) +
                (Integer.parseInt(rawTempValue.substring(rtlen-1), 16) / 16f);
        return(temp);
        }

    /**Get temperature as Float, else return null if none. */
    public Float getTemperature()
        {
        final String t = sectionsByKey.get(CommonSensorLabels.TEMPERATURE.getLabel());
        if(null == t) { return(null); }
        return(parseTemperatureFromDDCH(t));
        }
    }