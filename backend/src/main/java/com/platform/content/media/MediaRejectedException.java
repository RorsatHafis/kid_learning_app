package com.platform.content.media;

/** An upload that isn't a picture or recording the platform can serve: said to the author as it is (400). */
public class MediaRejectedException extends RuntimeException {

    public MediaRejectedException(String message) {
        super(message);
    }

}
