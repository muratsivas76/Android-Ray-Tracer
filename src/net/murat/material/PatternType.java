package net.murat.material;

/**
 * Enum defining the available procedural pattern types for GeneratedMaterial. Each pattern uses the
 * surface point coordinates and/or normal to generate a color variation between color1 and color2.
 */
public enum PatternType {
  /** Solid color - uses only color1, ignores color2. */
  SOLID,

  /** Checkerboard pattern - alternating squares based on X/Z coordinates. */
  CHECKERBOARD,

  /** Vertical gradient - transitions from bottom to top based on normal's Y component. */
  VERTICAL_GRADIENT,

  /** Horizontal gradient - transitions based on normal's X component. */
  HORIZONTAL_GRADIENT,

  /** Radial gradient - concentric rings radiating outward from origin. */
  RADIAL_GRADIENT,

  /** Wood rings - simulated tree growth rings with organic variation. */
  WOOD_RINGS,

  /** Marble veins - organic vein-like patterns. */
  MARBLE,

  /** Brick pattern - staggered rectangular bricks. */
  BRICK,

  /** Stripes - alternating parallel stripes. */
  STRIPES,

  /** Dots - circular dot pattern. */
  DOTS,

  /** Value noise - random-looking smooth noise. */
  NOISE,

  /** Fractal noise - multi-octave value noise for more detail. */
  FRACTAL
}
