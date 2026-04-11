const pool = require('../config/db');
const path = require('path');
const fs = require('fs');

// POST /api/products - Add product with images
exports.addProduct = async (req, res) => {
  try {
    const { name, category, description, price, quantity, unit, location } = req.body;
    const farmerId = req.user.id;

    const [result] = await pool.query(
      'INSERT INTO products (farmer_id, name, category, description, price, quantity, unit, location) VALUES (?, ?, ?, ?, ?, ?, ?, ?)',
      [farmerId, name, category, description, price, quantity, unit || 'kg', location || null]
    );

    const productId = result.insertId;

    // Handle uploaded images
    if (req.files && req.files.length > 0) {
      for (let i = 0; i < req.files.length; i++) {
        const imageUrl = `/uploads/${req.files[i].filename}`;
        await pool.query(
          'INSERT INTO product_images (product_id, image_url, is_primary) VALUES (?, ?, ?)',
          [productId, imageUrl, i === 0 ? 1 : 0]
        );
      }
    }

    // Get the created product with images
    const [products] = await pool.query(
      `SELECT p.*, GROUP_CONCAT(pi.image_url) as images
       FROM products p
       LEFT JOIN product_images pi ON p.id = pi.product_id
       WHERE p.id = ?
       GROUP BY p.id`,
      [productId]
    );

    res.status(201).json({
      message: 'Product added successfully!',
      product: {
        ...products[0],
        images: products[0].images ? products[0].images.split(',') : []
      }
    });
  } catch (err) {
    console.error('Add product error:', err);
    res.status(500).json({ error: 'Server error while adding product.' });
  }
};

// GET /api/products - Browse all products with search and filter
exports.getAllProducts = async (req, res) => {
  try {
    const { search, category, page = 1, limit = 12, sort = 'newest' } = req.query;
    const offset = (page - 1) * limit;

    let query = `
      SELECT p.*, u.name as farmer_name, u.location as farmer_location,
        (SELECT pi.image_url FROM product_images pi WHERE pi.product_id = p.id AND pi.is_primary = 1 LIMIT 1) as primary_image,
        (SELECT GROUP_CONCAT(pi.image_url) FROM product_images pi WHERE pi.product_id = p.id) as images
      FROM products p
      JOIN users u ON p.farmer_id = u.id
      WHERE p.status = 'active' AND p.quantity > 0
    `;
    let countQuery = `SELECT COUNT(*) as total FROM products p WHERE p.status = 'active' AND p.quantity > 0`;
    const params = [];
    const countParams = [];

    if (search) {
      query += ' AND (p.name LIKE ? OR p.description LIKE ?)';
      countQuery += ' AND (p.name LIKE ? OR p.description LIKE ?)';
      params.push(`%${search}%`, `%${search}%`);
      countParams.push(`%${search}%`, `%${search}%`);
    }

    if (category && category !== 'all') {
      query += ' AND p.category = ?';
      countQuery += ' AND p.category = ?';
      params.push(category);
      countParams.push(category);
    }

    // Sorting
    switch (sort) {
      case 'price_low': query += ' ORDER BY p.price ASC'; break;
      case 'price_high': query += ' ORDER BY p.price DESC'; break;
      case 'oldest': query += ' ORDER BY p.created_at ASC'; break;
      default: query += ' ORDER BY p.created_at DESC';
    }

    query += ' LIMIT ? OFFSET ?';
    params.push(parseInt(limit), parseInt(offset));

    const [products] = await pool.query(query, params);
    const [countResult] = await pool.query(countQuery, countParams);

    const formattedProducts = products.map(p => ({
      ...p,
      images: p.images ? p.images.split(',') : [],
    }));

    res.json({
      products: formattedProducts,
      pagination: {
        total: countResult[0].total,
        page: parseInt(page),
        limit: parseInt(limit),
        totalPages: Math.ceil(countResult[0].total / limit)
      }
    });
  } catch (err) {
    console.error('Get all products error:', err);
    res.status(500).json({ error: 'Server error.' });
  }
};

// GET /api/products/:id - Get single product
exports.getProductById = async (req, res) => {
  try {
    const [products] = await pool.query(
      `SELECT p.*, u.name as farmer_name, u.email as farmer_email, u.phone as farmer_phone, u.location as farmer_location, u.business_name as farm_name,
        (SELECT GROUP_CONCAT(pi.image_url) FROM product_images pi WHERE pi.product_id = p.id) as images
       FROM products p
       JOIN users u ON p.farmer_id = u.id
       WHERE p.id = ?`,
      [req.params.id]
    );

    if (products.length === 0) {
      return res.status(404).json({ error: 'Product not found.' });
    }

    const product = products[0];
    res.json({
      ...product,
      images: product.images ? product.images.split(',') : []
    });
  } catch (err) {
    console.error('Get product error:', err);
    res.status(500).json({ error: 'Server error.' });
  }
};

// GET /api/products/farmer/mine - Get farmer's own products
exports.getMyProducts = async (req, res) => {
  try {
    const [products] = await pool.query(
      `SELECT p.*,
        (SELECT pi.image_url FROM product_images pi WHERE pi.product_id = p.id AND pi.is_primary = 1 LIMIT 1) as primary_image,
        (SELECT GROUP_CONCAT(pi.image_url) FROM product_images pi WHERE pi.product_id = p.id) as images
       FROM products p
       WHERE p.farmer_id = ?
       ORDER BY p.created_at DESC`,
      [req.user.id]
    );

    const formattedProducts = products.map(p => ({
      ...p,
      images: p.images ? p.images.split(',') : []
    }));

    res.json({ products: formattedProducts });
  } catch (err) {
    console.error('Get my products error:', err);
    res.status(500).json({ error: 'Server error.' });
  }
};

// PUT /api/products/:id - Update product
exports.updateProduct = async (req, res) => {
  try {
    const { name, category, description, price, quantity, unit, location, status } = req.body;
    const productId = req.params.id;

    // Check ownership
    const [existing] = await pool.query('SELECT farmer_id FROM products WHERE id = ?', [productId]);
    if (existing.length === 0) return res.status(404).json({ error: 'Product not found.' });
    if (existing[0].farmer_id !== req.user.id && req.user.role !== 'admin') {
      return res.status(403).json({ error: 'Not authorized to update this product.' });
    }

    await pool.query(
      'UPDATE products SET name = ?, category = ?, description = ?, price = ?, quantity = ?, unit = ?, location = ?, status = ? WHERE id = ?',
      [name, category, description, price, quantity, unit, location, status || 'active', productId]
    );

    // Handle new images if uploaded
    if (req.files && req.files.length > 0) {
      for (let i = 0; i < req.files.length; i++) {
        const imageUrl = `/uploads/${req.files[i].filename}`;
        await pool.query(
          'INSERT INTO product_images (product_id, image_url, is_primary) VALUES (?, ?, ?)',
          [productId, imageUrl, 0]
        );
      }
    }

    res.json({ message: 'Product updated successfully.' });
  } catch (err) {
    console.error('Update product error:', err);
    res.status(500).json({ error: 'Server error.' });
  }
};

// DELETE /api/products/:id - Delete product
exports.deleteProduct = async (req, res) => {
  try {
    const productId = req.params.id;

    // Check ownership
    const [existing] = await pool.query('SELECT farmer_id FROM products WHERE id = ?', [productId]);
    if (existing.length === 0) return res.status(404).json({ error: 'Product not found.' });
    if (existing[0].farmer_id !== req.user.id && req.user.role !== 'admin') {
      return res.status(403).json({ error: 'Not authorized to delete this product.' });
    }

    // Delete product images from disk
    const [images] = await pool.query('SELECT image_url FROM product_images WHERE product_id = ?', [productId]);
    images.forEach(img => {
      const filePath = path.join(__dirname, '..', img.image_url);
      if (fs.existsSync(filePath)) fs.unlinkSync(filePath);
    });

    await pool.query('DELETE FROM products WHERE id = ?', [productId]);

    res.json({ message: 'Product deleted successfully.' });
  } catch (err) {
    console.error('Delete product error:', err);
    res.status(500).json({ error: 'Server error.' });
  }
};
