/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://jersey.dev.java.net/CDDL+GPL.html
 * or jersey/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at jersey/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package com.sun.jersey.core.header.reader;

import com.sun.jersey.core.header.MediaTypes;
import com.sun.jersey.core.header.AcceptableLanguageTag;
import com.sun.jersey.core.header.AcceptableMediaType;
import com.sun.jersey.core.header.AcceptableToken;
import com.sun.jersey.core.header.HttpDateFormat;
import com.sun.jersey.core.header.QualityFactor;
import com.sun.jersey.core.header.QualitySourceMediaType;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.NewCookie;

/**
 * A pull-based reader of HTTP headers.
 * 
 * @author Paul.Sandoz@Sun.Com
 */
public abstract class HttpHeaderReader {
    
    public enum Event {
        Token, QuotedString, Comment, Separator, Control;
    };
    
    public abstract boolean hasNext();
    
    public abstract boolean hasNextSeparator(char separator, boolean skipWhiteSpace);
    
    public abstract Event next() throws ParseException;

    public abstract Event next(boolean skipWhiteSpace) throws ParseException;
    
    public abstract Event getEvent();
    
    public abstract String getEventValue();
    
    public abstract String getRemainder();
    
    public abstract int getIndex();
    
    public String nextToken() throws ParseException {
        Event e = next(false);
        if (e != Event.Token)
            throw new ParseException("Next event is not a Token", getIndex());
        
        return getEventValue();
    }
    
    public char nextSeparator() throws ParseException {         
        Event e = next(false);
        if (e != Event.Separator)
            throw new ParseException("Next event is not a Separator", getIndex());
        
        return getEventValue().charAt(0);
    }
    
    public void nextSeparator(char c) throws ParseException {         
        Event e = next(false);
        if (e != Event.Separator)
            throw new ParseException("Next event is not a Separator", getIndex());
        
        if (c != getEventValue().charAt(0)) {
            throw new ParseException("Expected separator '" + c + "' instead of '" 
                    + getEventValue().charAt(0) + "'", getIndex());
        }
    }
    
    public String nextQuotedString() throws ParseException { 
        Event e = next(false);
        if (e != Event.QuotedString)
            throw new ParseException("Next event is not a Quoted String", getIndex());
        
        return getEventValue();
    }
        
    public String nextTokenOrQuotedString() throws ParseException {
        Event e = next(false);
        if (e != Event.Token && e != Event.QuotedString)
            throw new ParseException("Next event is not a Token or a Quoted String, " + 
                    getEventValue(), getIndex());
        
        return getEventValue();
    }
    
    
    
    
    public static HttpHeaderReader newInstance(String header) {
        return new HttpHeaderReaderImpl(header);
    }
    
    public static HttpHeaderReader newInstance(String header, boolean processComments) {
        return new HttpHeaderReaderImpl(header, processComments);
    }


    public static Date readDate(String date) throws ParseException {
        ParseException pe = null;
        for (SimpleDateFormat f : HttpDateFormat.getDateFormats()) {
            try {
                return f.parse(date);
            } catch (ParseException e) {
                pe = (pe == null) ? e : pe;
            }
        }
        
        throw pe;
    }


    public static int readQualityFactor(String q) throws ParseException {
        if (q == null || q.length() == 0)
            throw new ParseException("Quality value cannot be null or an empty String", 0);
        
        int index = 0;
        final int length = q.length();
        if (length > 5) {
            throw new ParseException("Quality value is greater than the maximum length, 5", 0);
        }
        
        // Parse the whole number and decimal point
        final char wholeNumber;
        char c = wholeNumber = q.charAt(index++);
        if (c == '0' || c == '1') {
            if (index == length)
                return (c - '0') * 1000;
            c = q.charAt(index++);
            if (c != '.') {
                throw new ParseException("Error parsing Quality value: a decimal place is expected rather than '" + 
                        c + "'", index);
            }
            if (index == length)
                return (c - '0') * 1000;
        } else if (c == '.') {
            // This is not conformant to the HTTP specification but some implementations
            // do this, for example HttpURLConnection.
            if (index == length)
                throw new ParseException("Error parsing Quality value: a decimal numeral is expected after the decimal point", index);
            
        } else {
            throw new ParseException("Error parsing Quality value: a decimal numeral '0' or '1' is expected rather than '" + 
                    c + "'", index);
        }

        // Parse the fraction
        int value = 0;
        int exponent = 100;
        while (index < length) {
            c = q.charAt(index++);
            if (c >= '0' && c <= '9') {
                value += (c - '0') * exponent;
                exponent /= 10;
            } else {
                throw new ParseException("Error parsing Quality value: a decimal numeral is expected rather than '" + 
                        c + "'", index);
            }
        }
        
        if (wholeNumber == '1') {
            if (value > 0)
                throw new ParseException("The Quality value, " + q + ", is greater than 1", index);
            return QualityFactor.DEFAULT_QUALITY_FACTOR;
        } else
            return value;
    }
    
    public static int readQualityFactorParameter(HttpHeaderReader reader) throws ParseException {
        int q = -1;
        while (reader.hasNext()) {
            reader.nextSeparator(';');
            
            // Ignore a ';' with no parameters
            if (!reader.hasNext())
                return QualityFactor.DEFAULT_QUALITY_FACTOR;
            
            // Get the parameter name
            String name = reader.nextToken();
            reader.nextSeparator('=');
            // Get the parameter value
            String value = reader.nextTokenOrQuotedString();
            
            if (q == -1 && name.equalsIgnoreCase(QualityFactor.QUALITY_FACTOR)) {
                q = readQualityFactor(value);
            }
        }
        
        return (q == -1) ? QualityFactor.DEFAULT_QUALITY_FACTOR : q;
    }
    
    public static Map<String, String> readParameters(HttpHeaderReader reader) throws ParseException {
        Map<String, String> m = null;
        
        while (reader.hasNext()) {
            reader.nextSeparator(';');
            while(reader.hasNextSeparator(';', true))
                reader.next();
            
            // Ignore a ';' with no parameters
            if (!reader.hasNext())
                break;
            
            // Get the parameter name
            String name = reader.nextToken();
            reader.nextSeparator('=');
            // Get the parameter value
            String value = reader.nextTokenOrQuotedString();
    
            if (m == null)
                m = new LinkedHashMap<String, String>();
            
            // Lower case the parameter name
            m.put(name.toLowerCase(), value);
        }
        
        return m;
    }

    public static Map<String, Cookie> readCookies(String header) {
        return CookiesParser.parseCookies(header);
    }

    public static Cookie readCookie(String header) {
        return CookiesParser.parseCookie(header);
    }

    public static NewCookie readNewCookie(String header) {
        return CookiesParser.parseNewCookie(header);
    }

    private static final ListElementCreator<AcceptableMediaType> ACCEPTABLE_MEDIA_TYPE_CREATOR =
            new ListElementCreator<AcceptableMediaType>() {
        public AcceptableMediaType create(HttpHeaderReader reader) throws ParseException {
            return AcceptableMediaType.valueOf(reader);
        }
    };

    private static final Comparator<AcceptableMediaType> ACCEPTABLE_MEDIA_TYPE_COMPARATOR
            = new Comparator<AcceptableMediaType>() {
        public int compare(AcceptableMediaType o1, AcceptableMediaType o2) {
            int i = o2.getQuality() - o1.getQuality();
            if (i != 0)
                return i;

            return MediaTypes.MEDIA_TYPE_COMPARATOR.compare(o1, o2);
        }
    };

    public static List<AcceptableMediaType> readAcceptMediaType(String header) throws ParseException {
        return HttpHeaderReader.readAcceptableList(
                ACCEPTABLE_MEDIA_TYPE_COMPARATOR,
                ACCEPTABLE_MEDIA_TYPE_CREATOR,
                header);
    }


    private static final ListElementCreator<QualitySourceMediaType> QUALITY_SOURCE_MEDIA_TYPE_CREATOR =
            new ListElementCreator<QualitySourceMediaType>() {
        public QualitySourceMediaType create(HttpHeaderReader reader) throws ParseException {
            return QualitySourceMediaType.valueOf(reader);
        }
    };

    private static final Comparator<QualitySourceMediaType> QUALITY_SOURCE_MEDIA_TYPE_COMPARATOR
            = new Comparator<QualitySourceMediaType>() {
        public int compare(QualitySourceMediaType o1, QualitySourceMediaType o2) {
            int i = o2.getQualitySource() - o1.getQualitySource();
            if (i != 0)
                return i;

            return MediaTypes.MEDIA_TYPE_COMPARATOR.compare(o1, o2);
        }
    };

    public static List<QualitySourceMediaType> readQualitySourceMediaType(String header) throws ParseException {
        return HttpHeaderReader.readAcceptableList(
                QUALITY_SOURCE_MEDIA_TYPE_COMPARATOR,
                QUALITY_SOURCE_MEDIA_TYPE_CREATOR,
                header);
    }

    public static List<QualitySourceMediaType> readQualitySourceMediaType(String[] header) throws ParseException {
        if (header.length < 2)
            return readQualitySourceMediaType(header[0]);
        
        StringBuffer sb = new StringBuffer();
        for (String h : header) {
            if (sb.length() > 0)
                sb.append(",");

            sb.append(h);
        }

        return readQualitySourceMediaType(sb.toString());
    }

    public static List<AcceptableMediaType> readAcceptMediaType(String header, final List<QualitySourceMediaType> priorityMediaTypes) throws ParseException {
        return HttpHeaderReader.readAcceptableList(
                new Comparator<AcceptableMediaType>() {
                   public int compare(AcceptableMediaType o1, AcceptableMediaType o2) {
//                       int i = 0;
                       boolean q_o1_set = false;
                       int q_o1 = QualitySourceMediaType.DEFAULT_QUALITY_SOURCE_FACTOR * QualitySourceMediaType.DEFAULT_QUALITY_SOURCE_FACTOR;
                       boolean q_o2_set = false;
                       int q_o2 = QualitySourceMediaType.DEFAULT_QUALITY_SOURCE_FACTOR * QualitySourceMediaType.DEFAULT_QUALITY_SOURCE_FACTOR;
                       for (QualitySourceMediaType m : priorityMediaTypes) {
                           if (!q_o1_set && MediaTypes.typeEquals(o1, m)) {
                               q_o1 = o1.getQuality() * m.getQualitySource();
                               q_o1_set = true;
                           } else if (!q_o2_set && MediaTypes.typeEquals(o2, m)) {
                               q_o2 = o2.getQuality() * m.getQualitySource();
                               q_o2_set = true;
                           }
//                           if (MediaTypes.typeEquals(o1, m))
//                               i = -1;
//                           else if (MediaTypes.typeEquals(o2, m))
//                               i = 1;
                       }
                       int i = q_o2 - q_o1;
                       if (i != 0)
                           return i;

                       i = o2.getQuality() - o1.getQuality();
                       if (i != 0)
                           return i;

                       return MediaTypes.MEDIA_TYPE_COMPARATOR.compare(o1, o2);
                   }
                },
                ACCEPTABLE_MEDIA_TYPE_CREATOR,
                header);
    }

    private static final ListElementCreator<AcceptableToken> ACCEPTABLE_TOKEN_CREATOR =
            new ListElementCreator<AcceptableToken>() {
        public AcceptableToken create(HttpHeaderReader reader) throws ParseException {
            return new AcceptableToken(reader);
        }
    };

    public static List<AcceptableToken> readAcceptToken(String header) throws ParseException {
        return HttpHeaderReader.readAcceptableList(ACCEPTABLE_TOKEN_CREATOR, header);
    }


    private static final ListElementCreator<AcceptableLanguageTag> LANGUAGE_CREATOR =
            new ListElementCreator<AcceptableLanguageTag>() {
        public AcceptableLanguageTag create(HttpHeaderReader reader) throws ParseException {
            return new AcceptableLanguageTag(reader);
        }
    };

    public static List<AcceptableLanguageTag> readAcceptLanguage(String header) throws ParseException {
        return HttpHeaderReader.readAcceptableList(LANGUAGE_CREATOR, header);
    }


    private static final Comparator<QualityFactor> QUALITY_COMPARATOR = new Comparator<QualityFactor>() {
        public int compare(QualityFactor o1, QualityFactor o2) {
            return o2.getQuality() - o1.getQuality();
        }
    };
    
    public static <T extends QualityFactor> List<T> readAcceptableList(
            ListElementCreator<T> c, 
            String header) throws ParseException {
        List<T> l = readList(c, header);
        Collections.sort(l, QUALITY_COMPARATOR);
        return l;
    }

    public static <T> List<T> readAcceptableList(
            Comparator<T> comparator,
            ListElementCreator<T> c,
            String header) throws ParseException {
        List<T> l = readList(c, header);
        Collections.sort(l, comparator);
        return l;
    }

    
    public static interface ListElementCreator<T> {
        T create(HttpHeaderReader reader)  throws ParseException;
    }

    public static <T> List<T> readList(ListElementCreator<T> c,
            String header) throws ParseException {
        List<T> l = new ArrayList<T>();
        HttpHeaderReader reader = new HttpHeaderReaderImpl(header);
        HttpHeaderListAdapter adapter = new HttpHeaderListAdapter(reader);
        while(reader.hasNext()) {
            l.add(c.create(adapter));
            adapter.reset();
            if (reader.hasNext())
                reader.next();
        }

        return l;
    }
}