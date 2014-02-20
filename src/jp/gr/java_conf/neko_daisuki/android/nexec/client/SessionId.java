package jp.gr.java_conf.neko_daisuki.android.nexec.client;

import android.os.Parcel;
import android.os.Parcelable;

public final class SessionId implements Parcelable {

    private static class Creator implements Parcelable.Creator<SessionId> {

        @Override
        public SessionId createFromParcel(Parcel source) {
            return new SessionId(source.readString());
        }

        @Override
        public SessionId[] newArray(int size) {
            return new SessionId[size];
        }
    }

    public static final Parcelable.Creator<SessionId> CREATOR = new Creator();
    public static final SessionId NULL = new SessionId("NULL");

    private String mId;

    public SessionId(String id) {
        mId = id;
    }

    public boolean isNull() {
        return mId.equals(NULL.toString());
    }

    @Override
    public boolean equals(Object o) {
        try {
            return ((SessionId)o).toString().equals(mId);
        }
        catch (ClassCastException e) {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return mId.hashCode();
    }

    @Override
    public String toString() {
        return mId;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(mId);
    }
}