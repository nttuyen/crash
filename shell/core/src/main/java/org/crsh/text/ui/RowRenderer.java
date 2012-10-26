/*
 * Copyright (C) 2012 eXo Platform SAS.
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

package org.crsh.text.ui;

import org.crsh.text.LineReader;
import org.crsh.text.RenderAppendable;
import org.crsh.text.Renderer;
import org.crsh.text.Style;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class RowRenderer extends Renderer {

  /** . */
  private final List<Renderer> cols;

  /** . */
  private final Style.Composite style;

  RowRenderer(RowElement row) {

    List<Renderer> cols = new ArrayList<Renderer>(row.cols.size());
    for (Element col : row.cols) {
      cols.add(col.renderer());
    }

    //
    this.cols = cols;
    this.style = row.getStyle();
  }

  int getSize() {
    return cols.size();
  }

  public List<Renderer> getCols() {
    return cols;
  }

  @Override
  public int getActualWidth() {
    int width = 0;
    for (Renderer col : cols) {
      width += col.getActualWidth();
    }
    return width;
  }

  @Override
  public int getMinWidth() {
    int minWidth = 0;
    for (Renderer col : cols) {
      minWidth += col.getMinWidth();
    }
    return minWidth;
  }

  @Override
  public int getActualHeight(int width) {
    int actualHeight = 0;
    for (Renderer col : cols) {
      actualHeight = Math.max(actualHeight, col.getActualHeight(width));
    }
    return actualHeight;
  }

  @Override
  public int getMinHeight(int width) {
    int minHeight = 0;
    for (Renderer col : cols) {
      minHeight = Math.max(minHeight, col.getMinHeight(width));
    }
    return minHeight;
  }

  // todo look at :
  // if (i > 0) {
  // to.append(b.horizontal);
  // }
  // in relation to widths array that can contain (should?) 0 value
  LineReader renderer(final int[] widths, final BorderStyle separator, int height) {
    final AtomicInteger totalWidth = new AtomicInteger();
    final LineReader[] readers = new LineReader[cols.size()];
    for (int i = 0;i < cols.size();i++) {
      if (widths[i] > 0) {
        LineReader reader = cols.get(i).reader(widths[i], height);
        readers[i] = reader;
        totalWidth.addAndGet(widths[i]);
      }
    }

    //
    return new LineReader() {

      /** . */
      private boolean done = false;

      public boolean hasLine() {
        return !done;
      }

      public void renderLine(RenderAppendable to) {
        if (!hasLine()) {
          throw new IllegalStateException();
        }

        if (style != null) {
          to.enterStyle(style);
        }
        for (int i = 0;i < readers.length;i++) {
          LineReader renderer = readers[i];
          if (widths[i] > 0) {
            if (i > 0) {
              if (separator != null) {
                to.styleOff();
                to.append(separator.vertical);
                to.styleOn();
              }
            }
            if (renderer != null && renderer.hasLine()) {
              renderer.renderLine(to);
            } else {
              readers[i] = null;
              for (int j = widths[i];j > 0;j--) {
                to.append(' ');
              }
            }
          }
        }
        if (style != null) {
          to.leaveStyle();
        }


        // Update status
        done = true;
        for (LineReader reader : readers) {
          if (reader != null) {
            if (reader.hasLine()) {
              done = false;
              break;
            }
          }
        }
      }
    };
  }

  @Override
  public LineReader reader(int width) {
    int[] widths = new int[cols.size()];
    int[] minWidths = new int[cols.size()];
    for (int i = 0;i < cols.size();i++) {
      Renderer renderable = cols.get(i);
      widths[i] = Math.max(widths[i], renderable.getActualWidth());
      minWidths[i] = Math.max(minWidths[i], renderable.getMinWidth());
    }

    //
    widths = Layout.flow().compute(false, width, widths, minWidths);

    //
    if (widths == null) {
      return new LineReader() {
        public boolean hasLine() {
          return false;
        }
        public void renderLine(RenderAppendable to) throws IllegalStateException {
          throw new IllegalStateException();
        }
      };
    } else {

      // Size could be smaller and lead to ArrayIndexOutOfBounds later
      widths = Arrays.copyOf(widths, minWidths.length);

      //
      return renderer(widths, null, -1);
    }
  }
}
