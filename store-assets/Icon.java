import java.awt.*; import java.awt.geom.*; import java.awt.image.BufferedImage; import javax.imageio.ImageIO; import java.io.File;
/** Draws the Wiz Mileage launcher icon (same geometry as ic_launcher_*.xml) at any size. */
public class Icon {
  static Color c(long argb){ return new Color((int)argb, true); }
  public static void main(String[] a) throws Exception {
    int S = Integer.parseInt(a[1]);
    BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = img.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    // Show the adaptive icon's visible 72x72 centre of the 108x108 canvas, slightly zoomed out for breathing room.
    double view = 76, s = S / view; g.scale(s, s); g.translate(-(108 - view) / 2, -(108 - view) / 2);
    // background
    g.setPaint(new RadialGradientPaint(new Point2D.Double(54,44), 78f, new float[]{0f,.55f,1f}, new Color[]{c(0xFF2C2C35L), c(0xFF141418L), c(0xFF08080AL)}));
    g.fill(new Rectangle2D.Double(-10,-10,128,128));
    g.setPaint(new RadialGradientPaint(new Point2D.Double(54,57), 30f, new float[]{0f,1f}, new Color[]{c(0x33FFB000L), c(0x00FFB000L)}));
    g.fill(new Ellipse2D.Double(24,27,60,60));
    // gauge
    BasicStroke thick = new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    g.setStroke(thick); g.setColor(c(0xFF34343EL));
    g.draw(new Arc2D.Double(30,33,48,48,210,-240,Arc2D.OPEN));
    g.setPaint(new LinearGradientPaint(30,70,76,44,new float[]{0f,.55f,1f}, new Color[]{c(0xFFFF5A1FL), c(0xFFFF9F0AL), c(0xFFFFD60AL)}));
    g.draw(new Arc2D.Double(30,33,48,48,210,-180,Arc2D.OPEN));
    // ticks
    g.setColor(c(0xFF6A6A78L));
    double[][] t = {{39.28,65.5},{39.28,48.5},{54,40},{68.72,48.5},{68.72,65.5}};
    for (double[] p : t) g.fill(new Ellipse2D.Double(p[0]-1.3,p[1]-1.3,2.6,2.6));
    // needle
    g.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.setColor(c(0x40FFD60AL));
    g.draw(new Line2D.Double(54,57,70.45,47.5));
    g.setStroke(new BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.setColor(Color.WHITE);
    g.draw(new Line2D.Double(54,57,70.45,47.5));
    // hub
    g.setColor(c(0xFFFFD60AL)); g.fill(new Ellipse2D.Double(54-5.6,57-5.6,11.2,11.2));
    g.setColor(c(0xFF141418L)); g.fill(new Ellipse2D.Double(54-2.2,57-2.2,4.4,4.4));
    // drop
    Path2D d = new Path2D.Double(); d.moveTo(54,69.5);
    d.curveTo(54,69.5,49.3,75.2,49.3,78.2); d.curveTo(49.3,80.8,51.4,82.9,54,82.9);
    d.curveTo(56.6,82.9,58.7,80.8,58.7,78.2); d.curveTo(58.7,75.2,54,69.5,54,69.5); d.closePath();
    g.setPaint(new GradientPaint(54,69,c(0xFFFFE45CL),54,83,c(0xFFFF9F0AL))); g.fill(d);
    Path2D h = new Path2D.Double(); h.moveTo(52.1,77.4); h.curveTo(52.1,76.3,52.7,75.2,53.3,74.4); h.curveTo(53.5,74.9,53.2,76.2,52.9,77.6); h.closePath();
    g.setColor(c(0x99FFFFFFL)); g.fill(h);
    g.dispose();
    // Play requires an opaque PNG.
    BufferedImage out = new BufferedImage(S, S, BufferedImage.TYPE_INT_RGB);
    Graphics2D o = out.createGraphics(); o.drawImage(img, 0, 0, null); o.dispose();
    ImageIO.write(out, "png", new File(a[0]));
  }
}
