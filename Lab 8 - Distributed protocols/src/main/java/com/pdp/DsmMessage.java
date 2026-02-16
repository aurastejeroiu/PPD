package com.pdp;

import java.io.Serializable;

/**
 * Serializable MPI payload.
 */
public class DsmMessage implements Serializable {

    public enum Kind {
        REQUEST_WRITE,
        REQUEST_CAS,
        ORDER_APPLY,
        STOP
    }

    public enum Op {
        WRITE,
        CAS
    }

    public Kind kind;
    public Op op; // only for ORDER_APPLY

    // Update data
    public String var;
    public int value;       // for WRITE
    public int expected;    // for CAS
    public int newValue;    // for CAS

    // Ordering
    public long seq;        // assigned by sequencer

    // Request tracking
    public int originRank;
    public long requestId;

    // CAS result (decided by sequencer)
    public boolean casSuccess;

    public static DsmMessage writeRequest(String var, int value, int originRank, long requestId) {
        DsmMessage m = new DsmMessage();
        m.kind = Kind.REQUEST_WRITE;
        m.var = var;
        m.value = value;
        m.originRank = originRank;
        m.requestId = requestId;
        return m;
    }

    public static DsmMessage casRequest(String var, int expected, int newValue, int originRank, long requestId) {
        DsmMessage m = new DsmMessage();
        m.kind = Kind.REQUEST_CAS;
        m.var = var;
        m.expected = expected;
        m.newValue = newValue;
        m.originRank = originRank;
        m.requestId = requestId;
        return m;
    }

    public static DsmMessage orderFromWrite(long seq, DsmMessage req) {
        DsmMessage m = new DsmMessage();
        m.kind = Kind.ORDER_APPLY;
        m.op = Op.WRITE;
        m.seq = seq;
        m.var = req.var;
        m.value = req.value;
        m.originRank = req.originRank;
        m.requestId = req.requestId;
        return m;
    }

    public static DsmMessage orderFromCas(long seq, DsmMessage req, boolean success) {
        DsmMessage m = new DsmMessage();
        m.kind = Kind.ORDER_APPLY;
        m.op = Op.CAS;
        m.seq = seq;
        m.var = req.var;
        m.expected = req.expected;
        m.newValue = req.newValue;
        m.originRank = req.originRank;
        m.requestId = req.requestId;
        m.casSuccess = success;
        return m;
    }

    public static DsmMessage stop() {
        DsmMessage m = new DsmMessage();
        m.kind = Kind.STOP;
        return m;
    }
}
