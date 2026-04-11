const pool = require('../config/db');

// GET /api/cart
exports.getCart = async (req, res) => {
  try {
    const [items] = await pool.query(
      `SELECT ci.id, ci.quantity, ci.product_id,
        p.name, p.price, p.unit, p.quantity as stock, p.category,
        u.name as farmer_name,
        (SELECT pi.image_url FROM product_images pi WHERE pi.product_id = p.id AND pi.is_primary = 1 LIMIT 1) as image
       FROM cart_items ci
       JOIN products p ON ci.product_id = p.id
       JOIN users u ON p.farmer_id = u.id
       WHERE ci.buyer_id = ?
       ORDER BY ci.created_at DESC`,
      [req.user.id]
    );

    const subtotal = items.reduce((sum, item) => sum + (item.price * item.quantity), 0);
    const shipping = subtotal > 0 ? 50 : 0;
    const tax = subtotal * 0.18;
    const total = subtotal + shipping + tax;

    res.json({
      items,
      summary: {
        subtotal: subtotal.toFixed(2),
        shipping: shipping.toFixed(2),
        tax: tax.toFixed(2),
        total: total.toFixed(2),
        itemCount: items.length
      }
    });
  } catch (err) {
    console.error('Get cart error:', err);
    res.status(500).json({ error: 'Server error.' });
  }
};

// POST /api/cart
exports.addToCart = async (req, res) => {
  try {
    const { productId, quantity = 1 } = req.body;

    // Check product exists and is active
    const [products] = await pool.query('SELECT id, quantity as stock, farmer_id FROM products WHERE id = ? AND status = "active"', [productId]);
    if (products.length === 0) return res.status(404).json({ error: 'Product not found or unavailable.' });

    // Can't buy own products
    if (products[0].farmer_id === req.user.id) {
      return res.status(400).json({ error: 'You cannot add your own product to cart.' });
    }

    if (quantity > products[0].stock) {
      return res.status(400).json({ error: 'Requested quantity exceeds available stock.' });
    }

    // Upsert cart item
    await pool.query(
      `INSERT INTO cart_items (buyer_id, product_id, quantity) VALUES (?, ?, ?)
       ON DUPLICATE KEY UPDATE quantity = quantity + VALUES(quantity)`,
      [req.user.id, productId, quantity]
    );

    res.status(201).json({ message: 'Item added to cart.' });
  } catch (err) {
    console.error('Add to cart error:', err);
    res.status(500).json({ error: 'Server error.' });
  }
};

// PUT /api/cart/:id
exports.updateCartItem = async (req, res) => {
  try {
    const { quantity } = req.body;
    const cartItemId = req.params.id;

    if (quantity <= 0) {
      await pool.query('DELETE FROM cart_items WHERE id = ? AND buyer_id = ?', [cartItemId, req.user.id]);
      return res.json({ message: 'Item removed from cart.' });
    }

    await pool.query(
      'UPDATE cart_items SET quantity = ? WHERE id = ? AND buyer_id = ?',
      [quantity, cartItemId, req.user.id]
    );

    res.json({ message: 'Cart updated.' });
  } catch (err) {
    console.error('Update cart error:', err);
    res.status(500).json({ error: 'Server error.' });
  }
};

// DELETE /api/cart/:id
exports.removeFromCart = async (req, res) => {
  try {
    await pool.query('DELETE FROM cart_items WHERE id = ? AND buyer_id = ?', [req.params.id, req.user.id]);
    res.json({ message: 'Item removed from cart.' });
  } catch (err) {
    console.error('Remove from cart error:', err);
    res.status(500).json({ error: 'Server error.' });
  }
};
