package de.thu.inf.spro.chattitude.backend.network;

class Packet {

    enum Type {

        INVALID,
        CONNECTED;

        private static Type[] _values = values();

        static Type from(int i){
            if(i < _values.length) return _values[i];
            return Type.INVALID;
        }

    }

}
