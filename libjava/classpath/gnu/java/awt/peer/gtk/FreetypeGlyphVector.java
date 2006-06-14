/* FreetypeGlyphVector.java
   Copyright (C) 2006  Free Software Foundation, Inc.

This file is part of GNU Classpath.

GNU Classpath is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2, or (at your option)
any later version.

GNU Classpath is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
General Public License for more details.

You should have received a copy of the GNU General Public License
along with GNU Classpath; see the file COPYING.  If not, write to the
Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
02110-1301 USA.

Linking this library statically or dynamically with other modules is
making a combined work based on this library.  Thus, the terms and
conditions of the GNU General Public License cover the whole
combination.

As a special exception, the copyright holders of this library give you
permission to link this library with independent modules to produce an
executable, regardless of the license terms of these independent
modules, and to copy and distribute the resulting executable under
terms of your choice, provided that you also meet, for each linked
independent module, the terms and conditions of the license of that
module.  An independent module is a module which is not derived from
or based on this library.  If you modify this library, you may extend
this exception to your version of the library, but you are not
obligated to do so.  If you do not wish to do so, delete this
exception statement from your version. */

package gnu.java.awt.peer.gtk;

import java.awt.Font;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.GeneralPath;
import java.awt.font.GlyphJustificationInfo;
import java.awt.font.GlyphMetrics;
import java.awt.font.GlyphVector;
import java.awt.font.FontRenderContext;

public class FreetypeGlyphVector extends GlyphVector
{
  /**
   * The associated font and its peer.
   */
  private Font font;
  private GdkFontPeer peer; // ATTN: Accessed from native code.

  private Rectangle2D logicalBounds;

  private float[] glyphPositions;
  /**
   * The string represented by this GlyphVector.
   */
  private String s;

  /**
   * The font render context
   */
  private FontRenderContext frc;

  /**
   * The total # of glyphs.
   */
  private int nGlyphs;

  /**
   * The glyph codes
   */
  private int[] glyphCodes;

  /**
   * Glyph transforms. (de facto only the translation is used)
   */
  private AffineTransform[] glyphTransforms;

  private GlyphMetrics[] metricsCache;

  /**
   * Create a glyphvector from a given (Freetype) font and a String.
   */
  public FreetypeGlyphVector(Font f, String s, FontRenderContext frc)
  {
    this(f, s, frc, Font.LAYOUT_LEFT_TO_RIGHT);
  }

  /**
   * Create a glyphvector from a given (Freetype) font and a String.
   */
  public FreetypeGlyphVector(Font f, String s, FontRenderContext frc,
			     int flags)
  {
    this.s = s;
    this.font = f;
    this.frc = frc;
    if( !(font.getPeer() instanceof GdkFontPeer ) )
      throw new IllegalArgumentException("Not a valid font.");
    peer = (GdkFontPeer)font.getPeer();

    getGlyphs();
    if( flags == Font.LAYOUT_RIGHT_TO_LEFT )
      {
	// reverse the glyph ordering.
	int[] temp = new int[ nGlyphs ];
	for(int i = 0; i < nGlyphs; i++)
	  temp[ i ] = glyphCodes[ nGlyphs - i - 1];
	glyphCodes = temp;
      }
    performDefaultLayout();
  }

  /**
   * Create a glyphvector from a given set of glyph codes.
   */
  public FreetypeGlyphVector(Font f, int[] codes, FontRenderContext frc)
  {
    this.font = f;
    this.frc = frc;
    if( !(font.getPeer() instanceof GdkFontPeer ) )
      throw new IllegalArgumentException("Not a valid font.");
    peer = (GdkFontPeer)font.getPeer();

    glyphCodes = new int[ codes.length ];
    System.arraycopy(codes, 0, glyphCodes, 0, codes.length);
    nGlyphs = glyphCodes.length;
    performDefaultLayout();
  }

  /**
   * Create the array of glyph codes.
   */
  private void getGlyphs()
  {
    nGlyphs = s.codePointCount( 0, s.length() );
    glyphCodes = new int[ nGlyphs ];
    int[] codePoints = new int[ nGlyphs ];
    int stringIndex = 0;

    for(int i = 0; i < nGlyphs; i++)
      {
	codePoints[i] = s.codePointAt( stringIndex );
	// UTF32 surrogate handling
	if( codePoints[i] != (int)s.charAt( stringIndex ) )
	  stringIndex ++;
	stringIndex ++;
      }

   glyphCodes = getGlyphs( codePoints );
  }

  /**
   * Returns the glyph code within the font for a given character
   */
  public native int[] getGlyphs(int[] codepoints);

  /**
   * Returns the kerning of a glyph pair
   */
  private native Point2D getKerning(int leftGlyph, int rightGlyph);

  private native double[] getMetricsNative( int glyphCode );

  private native GeneralPath getGlyphOutlineNative(int glyphIndex);

  /**
   * Duh, compares two instances.
   */
  public boolean equals(GlyphVector gv)
  {
    if( ! (gv instanceof FreetypeGlyphVector) )
      return false;

    return (((FreetypeGlyphVector)gv).font.equals(font) && 
	    ((FreetypeGlyphVector)gv).frc.equals(frc)
	    && ((FreetypeGlyphVector)gv).s.equals(s));
  }

  /**
   * Returns the associated Font
   */
  public Font getFont()
  {
    return font;
  }

  /**
   * Returns the associated FontRenderContext
   */
  public FontRenderContext getFontRenderContext()
  {
    return frc;
  }

  /**
   * Layout the glyphs.
   */
  public void performDefaultLayout()
  {
    logicalBounds = null; // invalidate caches.
    glyphPositions = null;

    glyphTransforms = new AffineTransform[ nGlyphs ]; 
    double x = 0;

    for(int i = 0; i < nGlyphs; i++)
      {
	GlyphMetrics gm = getGlyphMetrics( i );
	glyphTransforms[ i ] = AffineTransform.getTranslateInstance(x, 0);
	x += gm.getAdvanceX();
	if( i > 0 )
	  {
	    Point2D p = getKerning( glyphCodes[ i - 1 ], glyphCodes[ i ] );
	    x += p.getX();
	  }
      }
  }

  /**
   * Returns the code of the glyph at glyphIndex;
   */
  public int getGlyphCode(int glyphIndex)
  {
    return glyphCodes[ glyphIndex ];
  }

  /**
   * Returns multiple glyphcodes.
   */
  public int[] getGlyphCodes(int beginGlyphIndex, int numEntries, 
			     int[] codeReturn)
  {
    int[] rval;

    if( codeReturn == null )
      rval = new int[ numEntries ];
    else
      rval = codeReturn;
    
    System.arraycopy(glyphCodes, beginGlyphIndex, rval, 0, numEntries);

    return rval;
  }

  /**
   * FIXME: Implement me.
   */
  public Shape getGlyphLogicalBounds(int glyphIndex)
  {
    GlyphMetrics gm = getGlyphMetrics( glyphIndex );
    if( gm == null )
      return null; 
    Rectangle2D r = gm.getBounds2D();
    return new Rectangle2D.Double( r.getX() - gm.getLSB(), r.getY(),
				   gm.getAdvanceX(), r.getHeight() );
  }

  /*
   * FIXME: Not all glyph types are supported.
   * (The JDK doesn't really seem to do so either)
   */
  public void setupGlyphMetrics()
  {
    metricsCache = new GlyphMetrics[ nGlyphs ];

    for(int i = 0; i < nGlyphs; i++)
      {
	GlyphMetrics gm = (GlyphMetrics)
	  peer.getGlyphMetrics( glyphCodes[ i ] );
	if( gm == null )
	  {
	    double[] val = getMetricsNative( glyphCodes[ i ] );
	    if( val == null )
	      gm = null;
	    else
	      {
		gm = new GlyphMetrics( true, 
				       (float)val[1], 
				       (float)val[2], 
				       new Rectangle2D.Double
				       ( val[3], val[4], 
					 val[5], val[6] ),
				       GlyphMetrics.STANDARD );
		peer.putGlyphMetrics( glyphCodes[ i ], gm );
	      }
	  }
	metricsCache[ i ] = gm;
      }
  }

  /**
   * Returns the metrics of a single glyph.
   */
  public GlyphMetrics getGlyphMetrics(int glyphIndex)
  {
    if( metricsCache == null )
      setupGlyphMetrics();

    return metricsCache[ glyphIndex ];
  }

  /**
   * Returns the outline of a single glyph.
   */
  public Shape getGlyphOutline(int glyphIndex)
  {
    GeneralPath gp = getGlyphOutlineNative( glyphCodes[ glyphIndex ] );
    gp.transform( glyphTransforms[ glyphIndex ] );
    return gp;
  }

  /**
   * Returns the position of a single glyph.
   */
  public Point2D getGlyphPosition(int glyphIndex)
  {
    return glyphTransforms[ glyphIndex ].transform( new Point2D.Double(0, 0),
						   null );
  }

  /**
   * Returns the positions of multiple glyphs.
   */
  public float[] getGlyphPositions(int beginGlyphIndex, int numEntries, 
				   float[] positionReturn)
  {
    if( glyphPositions != null )
      return glyphPositions;

    float[] rval;

    if( positionReturn == null )
      rval = new float[2 * numEntries];
    else
      rval = positionReturn;

    for( int i = beginGlyphIndex; i < numEntries; i++ )
      {
	Point2D p = getGlyphPosition( i );
	rval[i * 2] = (float)p.getX();
	rval[i * 2 + 1] = (float)p.getY();
      }

    glyphPositions = rval;
    return rval;
  }

  /**
   * Returns the transform of a glyph.
   */
  public AffineTransform getGlyphTransform(int glyphIndex)
  {
    return new AffineTransform( glyphTransforms[ glyphIndex ] );
  }

  /**
   * Returns the visual bounds of a glyph
   * May be off by a pixel or two due to hinting/rasterization.
   */
  public Shape getGlyphVisualBounds(int glyphIndex)
  {
    return getGlyphOutline( glyphIndex ).getBounds2D();
  }

  /**
   * Return the logical bounds of the whole thing.
   */
  public Rectangle2D getLogicalBounds()
  {
    if( nGlyphs == 0 )
      return new Rectangle2D.Double(0, 0, 0, 0);
    if( logicalBounds != null )
      return logicalBounds;

    Rectangle2D rect = (Rectangle2D)getGlyphLogicalBounds( 0 );
    for( int i = 1; i < nGlyphs; i++ )
      {
	Rectangle2D r2 = (Rectangle2D)getGlyphLogicalBounds( i );
	Point2D p = getGlyphPosition( i );
	r2.setRect( p.getX(), p.getY(), r2.getWidth(), r2.getHeight() );
	rect = rect.createUnion( r2 );
      }

    logicalBounds = rect;
    return rect;
  }

  /**
   * Returns the number of glyphs.
   */
  public int getNumGlyphs()
  {
    return glyphCodes.length;
  }

  /**
   * Returns the outline of the entire GlyphVector.
   */
  public Shape getOutline()
  {
    GeneralPath path = new GeneralPath();
    for( int i = 0; i < getNumGlyphs(); i++ )
      path.append( getGlyphOutline( i ), false );
    return path;
  }

  /**
   * TODO: 
   * FreeType does not currently have an API for the JSTF table. We should 
   * probably get the table ourselves from FT and pass it to some parser 
   * which the native font peers will need.
   */
  public GlyphJustificationInfo getGlyphJustificationInfo(int glyphIndex)
  {
    return null;
  }

  /**
   * Returns the outline of the entire vector, drawn at (x,y).
   */
  public Shape getOutline(float x, float y)
  {
    AffineTransform tx = AffineTransform.getTranslateInstance( x, y );
    GeneralPath gp = (GeneralPath)getOutline();
    gp.transform( tx );
    return gp;
  }

  /**
   * Returns the visual bounds of the entire GlyphVector.
   * May be off by a pixel or two due to hinting/rasterization.
   */
  public Rectangle2D getVisualBounds()
  {
    return getOutline().getBounds2D();
  }

  /**
   * Sets the position of a glyph.
   */
  public void setGlyphPosition(int glyphIndex, Point2D newPos)
  {
    // FIXME: Scaling, etc.?
    glyphTransforms[ glyphIndex ].setToTranslation( newPos.getX(), 
						    newPos.getY() );
    logicalBounds = null;
    glyphPositions = null;
  }

  /**
   * Sets the transform of a single glyph.
   */
  public void setGlyphTransform(int glyphIndex, AffineTransform newTX)
  {
    glyphTransforms[ glyphIndex ].setTransform( newTX );
    logicalBounds = null;
    glyphPositions = null;
  }
}
