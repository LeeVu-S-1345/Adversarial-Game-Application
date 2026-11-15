package netpro.client.ui;

import java.util.List;

public interface PushAware {
    void onPush(List<String> msg);   // msg.get(0) là tên push: INVI, IDCL, OPPM, SKIP, OVER (STRT do SceneManager xử lý)
}