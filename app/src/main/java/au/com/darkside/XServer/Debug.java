package au.com.darkside.XServer;

import java.util.Locale;

import android.util.Log;

class Debug {

    private static final String LOG_TAG = "X Server";
    private static final Locale LOCALE = Locale.ROOT;

    public static void logQueryExtension(XServer xServer, String name,
                                         Object extension) {
        String fmt = "QueryExtension: name=%s, extension=%s";
        Log.d(LOG_TAG, String.format(LOCALE, fmt, name, extension));
    }

    public static void logChangeProperty(XServer xServer, int property,
                                         int type, int format, int length,
                                         int mode) {
        String fmt = "ChangeProperty: property=%s, type=%s, format=%d, length=%d, mode=%d";
        String s = getNameOfAtom(xServer, property);
        String t = getNameOfAtom(xServer, type);
        Log.d(LOG_TAG, String.format(LOCALE, fmt, s, t, format, length, mode));
    }

    public static void logGetProperty(XServer xServer, int property, int type,
                                      int offset, int length) {
        String fmt = "GetProperty: property=%s, type=%s, offset=%d, length=%d";
        String s = getNameOfAtom(xServer, property);
        String t = getNameOfAtom(xServer, type);
        Log.d(LOG_TAG, String.format(LOCALE, fmt, s, t, offset, length));
    }

    private static String getNameOfAtom(XServer xServer, int atom) {
        return xServer.getAtom(atom).getName();
    }
}