/*
 * Copyright (C) 2017 Dennis Neufeld
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package space.npstr.wolfia.utils;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;
import space.npstr.wolfia.Config;
import space.npstr.wolfia.Wolfia;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by napster on 06.07.17.
 * <p>
 * Provides urls from an imgur album
 */
public class ImgurAlbum {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(ImgurAlbum.class);

    //https://regex101.com/r/0TDxsu/2
    private static final Pattern IMGUR_ALBUM = Pattern.compile("^https?://imgur\\.com/a/([a-zA-Z0-9]+)$");

    private volatile String etag = "";

    //contains the images that this class randomly serves
    private volatile String[] urls = {"http://i.imgur.com/KLXYGHG.png"};

    private int lastIndex = -1;

    public ImgurAlbum(final String imgurAlbum) {
        //update the album every hour or so

        Wolfia.scheduleAtFixedRate(() -> {
            populateItems(imgurAlbum);
        }, 0, 1, TimeUnit.HOURS);
    }

    public void setLastIndex(final int lastIndex) {
        this.lastIndex = lastIndex;
    }

    public int getLastIndex() {
        return this.lastIndex;
    }

    //if index is out of bounds will return the url at index 0
    public String get(final int index) {
        return index >= this.urls.length || index < 0 ? this.urls[0] : this.urls[index];
    }

    public String getNext() {
        this.lastIndex++;
        if (this.lastIndex > this.urls.length) this.lastIndex = 0;
        return this.urls[this.lastIndex];
    }

    public String getRandomImageUrl() {
        this.lastIndex = ThreadLocalRandom.current().nextInt(this.urls.length);
        return this.urls[this.lastIndex];
    }

    /**
     * Updates the imgur backed images managed by this object
     */
    private void populateItems(final String imgurAlbumUrl) {

        final Matcher m = IMGUR_ALBUM.matcher(imgurAlbumUrl);

        if (!m.find()) {
            log.error("Not a valid imgur album url " + imgurAlbumUrl);
            return;
        }

        final String albumId = m.group(1);
        final HttpResponse<JsonNode> response;
        try {
            synchronized (this) {
                response = Unirest.get("https://api.imgur.com/3/album/" + albumId)
                        .header("Authorization", "Client-ID " + Config.C.imgurClientId)
                        .header("If-None-Match", this.etag)
                        .asJson();
            }
        } catch (final UnirestException e) {
            log.error("Imgur down? Could not fetch imgur album " + imgurAlbumUrl, e);
            return;
        }

        if (response.getStatus() == 200) {
            final JSONArray images = response.getBody().getObject().getJSONObject("data").getJSONArray("images");
            final List<String> imageUrls = new ArrayList<>();
            images.forEach(o -> imageUrls.add(((JSONObject) o).getString("link")));

            synchronized (this) {
                this.urls = imageUrls.toArray(this.urls);
                this.etag = response.getHeaders().getFirst("ETag");
            }
            log.info("Refreshed imgur album " + imgurAlbumUrl + ", new data found.");
        }
        //etag implementation: nothing has changed
        //https://api.imgur.com/performancetips
        else if (response.getStatus() == 304) {
            //nothing to do here
            log.info("Refreshed imgur album " + imgurAlbumUrl + ", no update.");
        } else {
            //some other status
            log.warn("Unexpected http status for imgur album request " + imgurAlbumUrl + ", response: " + response.getBody().toString());
        }
    }

}
