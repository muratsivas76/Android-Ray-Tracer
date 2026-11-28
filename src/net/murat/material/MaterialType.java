package net.murat.material;

/**
 * Enum defining the primary material categories. Each type has default parameter presets for the
 * material generator. Used for UI templates and quick material creation.
 */
public enum MaterialType {
  /**
   * Standard plastic material - matte to glossy finish Default: moderate reflectivity, no
   * transparency, medium shininess
   */
  PLASTIC,

  /**
   * Metallic material - highly reflective, conductive appearance Default: high reflectivity, low
   * roughness, strong Fresnel effect
   */
  METAL,

  /**
   * Glass/transparent material - refractive, partially reflective Default: high transparency, IOR
   * ~1.5, Fresnel effect enabled
   */
  GLASS,

  /**
   * Snow globe style - vertical gradient (white top, green bottom) Default: matte, rough, gradient
   * pattern
   */
  SNOW_GLOBE,

  /**
   * Carpet/fabric material - soft, matte, patterned Default: zero reflectivity, high roughness,
   * checkerboard pattern
   */
  CARPET,

  /**
   * Wood material - ring patterns, moderate reflectivity Default: wood ring pattern, semi-gloss
   * finish
   */
  WOOD,

  /**
   * Marble material - veined patterns, polished finish Default: marble pattern, moderate
   * reflectivity, low roughness
   */
  MARBLE,

  /**
   * Leather material - matte, slightly rough, soft appearance Default: low reflectivity, moderate
   * roughness
   */
  LEATHER,

  /**
   * Fabric/cloth material - very matte, no specular Default: zero reflectivity, maximum roughness
   */
  FABRIC,

  /**
   * Rubber material - very matte, soft, non-reflective Default: zero reflectivity, high roughness,
   * no specular
   */
  RUBBER,

  /** Custom user-defined material All parameters must be explicitly set by the user */
  CUSTOM
}
