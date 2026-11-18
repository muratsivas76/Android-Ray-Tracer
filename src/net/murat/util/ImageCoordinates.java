package net.murat.util;

/**
 * ImageCoordinates class stores image file path and its position/size information. Used for texture
 * mapping and image placement in the scene.
 *
 * @author Murat Inan
 */
public class ImageCoordinates {
  private String imagePath;
  private int x;
  private int y;
  private int width;
  private int height;

  /**
   * Constructor with all parameters
   *
   * @param imagePath the file path of the image
   * @param x X coordinate position
   * @param y Y coordinate position
   * @param width width of the image
   * @param height height of the image
   */
  public ImageCoordinates(String imagePath, int x, int y, int width, int height) {
    this.imagePath = imagePath;
    this.x = x;
    this.y = y;
    this.width = width;
    this.height = height;
  }

  // Getter methods
  public String getImagePath() {
    return imagePath;
  }

  public int getX() {
    return x;
  }

  public int getY() {
    return y;
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

  // Setter methods
  public void setImagePath(String imagePath) {
    this.imagePath = imagePath;
  }

  public void setX(int x) {
    this.x = x;
  }

  public void setY(int y) {
    this.y = y;
  }

  public void setWidth(int width) {
    this.width = width;
  }

  public void setHeight(int height) {
    this.height = height;
  }

  /**
   * Returns a string representation of the ImageCoordinates object
   *
   * @return formatted string with image coordinates
   */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("    imageCoordinates = \"");
    sb.append(imagePath);
    sb.append("\", ");
    sb.append(x);
    sb.append(", ");
    sb.append(y);
    sb.append(", ");
    sb.append(width);
    sb.append(", ");
    sb.append(height);
    sb.append(";\n");
    return sb.toString();
  }
}
