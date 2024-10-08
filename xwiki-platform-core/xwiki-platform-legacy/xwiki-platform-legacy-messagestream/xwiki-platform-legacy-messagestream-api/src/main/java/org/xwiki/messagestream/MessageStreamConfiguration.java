/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.messagestream;

import org.xwiki.component.annotation.Role;

/**
 * Configuration for the Message Stream application.
 *
 * @version $Id$
 * @since 10.5RC1
 * @since 9.11.6
 * @deprecated MessageStream should not be used anymore, please check extensions for alternatives.
 */
@Role
@Deprecated(since = "16.8.0RC1")
public interface MessageStreamConfiguration
{
    /**
     * @param wikiId the id of a wiki
     * @return either or not the message stream uis active on the given wiki
     */
    boolean isActive(String wikiId);
}
