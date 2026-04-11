const pool = require('../config/db');

// GET /api/wishlist
exports.getWishlist = async (req, res) => {
  try {
    const [items] = await pool.query(
      `SELECT w.id, w.product_id, w.created_at,
        p.name, p.price, p.unit, p.category, p.quantity as stock, p.status,
        u.name as farmer_name,
        (SELECT pi.image_url FROM product_images pi WHERE pi.product_id = p.id AND pi.is_primary = 1 LIMIT 1) as image
       FROM wishlist w
       JOIN products p ON w.product_id = p.id
       JOIN users u ON p.farmer_id = u.id
       WHERE w.buyer_id = ?
       ORDER BY w.created_at DESC`,
      [req.user.id]
    );

    res.json({ items });
  } catch (err) {
    console.error('Get wishlist error:', err);
    res.status(500).json({ error: 'Server error.' });
  }
};

// POST /api/wishlist
exports.addToWishlist = async (req, res) => {
  try {
    const { productId } = req.body;

    // Check product exists
    const [products] = await pool.query('SELECT id FROM products WHERE id = ?', [productId]);
    if (products.length === 0) return res.status(404).json({ error: 'Product not found.' });

    await pool.query(
      'INSERT IGNORE INTO wishlist (buyer_id, product_id) VALUES (?, ?)',
      [req.user.id, productId]
    );

    res.status(201).json({ message: 'Added to wishlist.' });
  } catch (err) {
    console.error('Add to wishlist error:', err);
    res.status(500).json({ error: 'Server error.' });
  }
};

// DELETE /api/wishlist/:productId
exports.removeFromWishlist = async (req, res) => {
  try {
    await pool.query(
      'DELETE FROM wishlist WHERE buyer_id = ? AND product_id = ?',
      [req.user.id, req.params.productId]
    );
    res.json({ message: 'Removed from wishlist.' });
  } catch (err) {
    console.error('Remove from wishlist error:', err);
    res.status(500).json({ error: 'Server error.' });
  }
};
