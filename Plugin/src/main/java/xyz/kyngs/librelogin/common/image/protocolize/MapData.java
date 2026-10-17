/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.image.protocolize;

/**
 * Encapsulates Minecraft map pixel buffer update data.
 *
 * @param columns the width of the pixel rectangle (number of columns)
 * @param rows the height of the pixel rectangle (number of rows)
 * @param posX the starting X offset on the map canvas
 * @param posZ the starting Z/Y offset on the map canvas
 * @param data the raw color index bytes representing the pixel payload
 */
public record MapData(int columns, int rows, int posX, int posZ, byte[] data) {}
