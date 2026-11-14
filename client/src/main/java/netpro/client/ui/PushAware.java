package netpro.client.ui;

import java.util.List;

public interface PushAware {
    void onPush(List<String> msg);   // msg.get(0) là tên lệnh: INVI, IDCL, OPPM, SKIP, OVER
}