package com.vortexwolf.chan.boards.fourchan.models;

import org.codehaus.jackson.annotate.JsonProperty;

public class FourchanThreadInfo {
    @JsonProperty("posts")
    public FourchanPostInfo[] posts;
}
