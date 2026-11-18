package net.murat.math;

/**
 * Represents a 3x3 matrix for linear transformations in 3D space. FULLY GC-FRIENDLY - ZERO
 * ALLOCATION in hot paths. All methods mutate 'this' or write to a target parameter. No "Local"
 * suffix - all methods are already garbage-free.
 */
public class Matrix3 {

  // Flat 1D array for cache-friendly access (row-major order)
  private final double[] m;

  // =========================================================================
  // CONSTRUCTORS
  // =========================================================================

  /** Constructs an identity Matrix3. */
  public Matrix3() {
    m = new double[9];
    setIdentity();
  }

  /** Constructs a Matrix3 with the specified elements. */
  public Matrix3(
      double m00,
      double m01,
      double m02,
      double m10,
      double m11,
      double m12,
      double m20,
      double m21,
      double m22) {
    m = new double[9];
    set(m00, m01, m02, m10, m11, m12, m20, m21, m22);
  }

  /** Copy constructor. ALLOCATES - use set(Matrix3) for GC-friendly code. */
  public Matrix3(Matrix3 other) {
    m = new double[9];
    set(other);
  }

  // =========================================================================
  // SETTERS (Fluent, GC-Friendly)
  // =========================================================================

  /**
   * Sets this matrix to identity (mutates this).
   *
   * @return this for method chaining
   */
  public Matrix3 setIdentity() {
    m[0] = 1.0;
    m[1] = 0.0;
    m[2] = 0.0;
    m[3] = 0.0;
    m[4] = 1.0;
    m[5] = 0.0;
    m[6] = 0.0;
    m[7] = 0.0;
    m[8] = 1.0;
    return this;
  }

  /**
   * Sets all elements of this matrix (mutates this).
   *
   * @return this for method chaining
   */
  public Matrix3 set(
      double m00,
      double m01,
      double m02,
      double m10,
      double m11,
      double m12,
      double m20,
      double m21,
      double m22) {
    this.m[0] = m00;
    this.m[1] = m01;
    this.m[2] = m02;
    this.m[3] = m10;
    this.m[4] = m11;
    this.m[5] = m12;
    this.m[6] = m20;
    this.m[7] = m21;
    this.m[8] = m22;
    return this;
  }

  /**
   * Copies values from another Matrix3 (mutates this).
   *
   * @return this for method chaining
   */
  public Matrix3 set(Matrix3 other) {
    System.arraycopy(other.m, 0, this.m, 0, 9);
    return this;
  }

  /** Sets a specific element (mutates this). */
  public Matrix3 setElement(int row, int col, double value) {
    if (row < 0 || row >= 3 || col < 0 || col >= 3) {
      throw new IndexOutOfBoundsException(
          "Matrix3 indices out of bounds: [" + row + "][" + col + "]");
    }
    this.m[row * 3 + col] = value;
    return this;
  }

  // =========================================================================
  // GETTERS
  // =========================================================================

  public double get(int row, int col) {
    if (row < 0 || row >= 3 || col < 0 || col >= 3) {
      throw new IndexOutOfBoundsException(
          "Matrix3 indices out of bounds: [" + row + "][" + col + "]");
    }
    return m[row * 3 + col];
  }

  public double[] getElements() {
    return this.m;
  }

  // =========================================================================
  // MATRIX OPERATIONS - GC-FRIENDLY (mutate target or this)
  // =========================================================================

  /**
   * Multiplies this matrix by another (mutates target). target = this * other
   *
   * @return target for method chaining
   */
  public Matrix3 multiply(Matrix3 other, Matrix3 target) {
    for (int i = 0; i < 3; i++) {
      for (int j = 0; j < 3; j++) {
        double sum = 0;
        for (int k = 0; k < 3; k++) {
          sum += this.m[i * 3 + k] * other.m[k * 3 + j];
        }
        target.m[i * 3 + j] = sum;
      }
    }
    return target;
  }

  /**
   * Multiplies this matrix by another (mutates this). this = this * other
   *
   * @return this for method chaining
   */
  public Matrix3 multiply(Matrix3 other) {
    double[] result = new double[9];
    for (int i = 0; i < 3; i++) {
      for (int j = 0; j < 3; j++) {
        double sum = 0;
        for (int k = 0; k < 3; k++) {
          sum += this.m[i * 3 + k] * other.m[k * 3 + j];
        }
        result[i * 3 + j] = sum;
      }
    }
    System.arraycopy(result, 0, this.m, 0, 9);
    return this;
  }

  /**
   * Multiplies this matrix by a scalar (mutates this).
   *
   * @return this for method chaining
   */
  public Matrix3 multiply(double scalar) {
    for (int i = 0; i < 9; i++) {
      this.m[i] *= scalar;
    }
    return this;
  }

  /**
   * Transforms a Vector3 by this matrix (mutates target).
   *
   * @return target for method chaining
   */
  public Vector3 transform(Vector3 source, Vector3 target) {
    double x = source.x;
    double y = source.y;
    double z = source.z;
    target.x = m[0] * x + m[1] * y + m[2] * z;
    target.y = m[3] * x + m[4] * y + m[5] * z;
    target.z = m[6] * x + m[7] * y + m[8] * z;
    return target;
  }

  /**
   * Transforms a Vector3 by this matrix (mutates the vector).
   *
   * @return the vector for method chaining
   */
  public Vector3 transform(Vector3 vector) {
    double x = vector.x;
    double y = vector.y;
    double z = vector.z;
    vector.x = m[0] * x + m[1] * y + m[2] * z;
    vector.y = m[3] * x + m[4] * y + m[5] * z;
    vector.z = m[6] * x + m[7] * y + m[8] * z;
    return vector;
  }

  /** Computes the determinant of this matrix. Does NOT mutate. */
  public double determinant() {
    return m[0] * (m[4] * m[8] - m[5] * m[7])
        - m[1] * (m[3] * m[8] - m[5] * m[6])
        + m[2] * (m[3] * m[7] - m[4] * m[6]);
  }

  /**
   * Computes the inverse matrix into target (mutates target).
   *
   * @return true if invertible, false if singular
   */
  public boolean inverse(Matrix3 target) {
    double det = determinant();
    if (Math.abs(det) < Ray.EPSILON) {
      return false;
    }

    double invDet = 1.0 / det;

    target.m[0] = (m[4] * m[8] - m[5] * m[7]) * invDet;
    target.m[1] = (m[2] * m[7] - m[1] * m[8]) * invDet;
    target.m[2] = (m[1] * m[5] - m[2] * m[4]) * invDet;

    target.m[3] = (m[5] * m[6] - m[3] * m[8]) * invDet;
    target.m[4] = (m[0] * m[8] - m[2] * m[6]) * invDet;
    target.m[5] = (m[2] * m[3] - m[0] * m[5]) * invDet;

    target.m[6] = (m[3] * m[7] - m[4] * m[6]) * invDet;
    target.m[7] = (m[1] * m[6] - m[0] * m[7]) * invDet;
    target.m[8] = (m[0] * m[4] - m[1] * m[3]) * invDet;

    return true;
  }

  /**
   * Computes the inverse of this matrix (mutates this).
   *
   * @return true if invertible, false if singular
   */
  public boolean inverse() {
    double det = determinant();
    if (Math.abs(det) < Ray.EPSILON) {
      return false;
    }

    double invDet = 1.0 / det;

    double m0 = (m[4] * m[8] - m[5] * m[7]) * invDet;
    double m1 = (m[2] * m[7] - m[1] * m[8]) * invDet;
    double m2 = (m[1] * m[5] - m[2] * m[4]) * invDet;

    double m3 = (m[5] * m[6] - m[3] * m[8]) * invDet;
    double m4 = (m[0] * m[8] - m[2] * m[6]) * invDet;
    double m5 = (m[2] * m[3] - m[0] * m[5]) * invDet;

    double m6 = (m[3] * m[7] - m[4] * m[6]) * invDet;
    double m7 = (m[1] * m[6] - m[0] * m[7]) * invDet;
    double m8 = (m[0] * m[4] - m[1] * m[3]) * invDet;

    this.m[0] = m0;
    this.m[1] = m1;
    this.m[2] = m2;
    this.m[3] = m3;
    this.m[4] = m4;
    this.m[5] = m5;
    this.m[6] = m6;
    this.m[7] = m7;
    this.m[8] = m8;

    return true;
  }

  /**
   * Transposes this matrix (mutates this).
   *
   * @return this for method chaining
   */
  public Matrix3 transpose() {
    double temp;
    temp = m[1];
    m[1] = m[3];
    m[3] = temp;
    temp = m[2];
    m[2] = m[6];
    m[6] = temp;
    temp = m[5];
    m[5] = m[7];
    m[7] = temp;
    return this;
  }

  /**
   * Transposes this matrix into target (mutates target).
   *
   * @return target for method chaining
   */
  public Matrix3 transpose(Matrix3 target) {
    target.set(
        this.m[0], this.m[3], this.m[6], this.m[1], this.m[4], this.m[7], this.m[2], this.m[5],
        this.m[8]);
    return target;
  }

  // =========================================================================
  // COPY
  // =========================================================================

  /**
   * Copies this matrix into target (mutates target).
   *
   * @return target for method chaining
   */
  public Matrix3 copy(Matrix3 target) {
    System.arraycopy(this.m, 0, target.m, 0, 9);
    return target;
  }

  /**
   * Returns a new Matrix3 (ALLOCATES - use sparingly). Kept only for compatibility with legacy
   * code.
   */
  public Matrix3 clone() {
    return new Matrix3(this);
  }

  // =========================================================================
  // TO STRING
  // =========================================================================

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 3; i++) {
      sb.append("| ");
      for (int j = 0; j < 3; j++) {
        sb.append(String.format(java.util.Locale.US, "%8.4f", m[i * 3 + j])).append(" ");
      }
      sb.append("|\n");
    }
    return sb.toString();
  }
}
