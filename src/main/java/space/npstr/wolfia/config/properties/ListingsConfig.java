/*
 * Copyright (C) 2017-2018 Dennis Neufeld
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

package space.npstr.wolfia.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Created by napster on 10.05.18.
 */
@Component
@ConfigurationProperties("listings")
public class ListingsConfig {

    private String dblToken = "";
    private String botsPwToken = "";
    private String carbonitexKey = "";

    public String getDblToken() {
        return dblToken;
    }

    public void setDblToken(final String dblToken) {
        this.dblToken = dblToken;
    }

    public String getBotsPwToken() {
        return botsPwToken;
    }

    public void setBotsPwToken(final String botsPwToken) {
        this.botsPwToken = botsPwToken;
    }

    public String getCarbonitexKey() {
        return carbonitexKey;
    }

    public void setCarbonitexKey(final String carbonitexKey) {
        this.carbonitexKey = carbonitexKey;
    }
}
