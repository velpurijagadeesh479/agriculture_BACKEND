const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const pool = require('../config/db');
require('dotenv').config();

// Generate a 6-digit verification code
const generateCode = () => {
  return Math.floor(100000 + Math.random() * 900000).toString();
};

// Generate JWT token
const generateToken = (user) => {
  return jwt.sign(
    { id: user.id, email: user.email, role: user.role, name: user.name },
    process.env.JWT_SECRET,
    { expiresIn: process.env.JWT_EXPIRES_IN || '7d' }
  );
};

// POST /api/auth/signup
exports.signup = async (req, res) => {
  try {
    const { name, email, password, phone, role, location, businessName } = req.body;

    // Check if user already exists
    const [existing] = await pool.query('SELECT id FROM users WHERE email = ?', [email]);
    if (existing.length > 0) {
      return res.status(400).json({ error: 'User with this email already exists.' });
    }

    // Hash password
    const salt = await bcrypt.genSalt(10);
    const hashedPassword = await bcrypt.hash(password, salt);

    // Insert user
    const [result] = await pool.query(
      'INSERT INTO users (name, email, password, phone, role, location, business_name) VALUES (?, ?, ?, ?, ?, ?, ?)',
      [name, email, hashedPassword, phone || null, role, location || null, businessName || null]
    );

    // Generate verification code
    const code = generateCode();
    const expiresAt = new Date(Date.now() + 10 * 60 * 1000); // 10 minutes

    // Delete old codes for this email
    await pool.query('DELETE FROM verification_codes WHERE email = ?', [email]);

    // Insert new code
    await pool.query(
      'INSERT INTO verification_codes (email, code, expires_at) VALUES (?, ?, ?)',
      [email, code, expiresAt]
    );

    res.status(201).json({
      message: 'Account created successfully. Please verify your account.',
      userId: result.insertId,
      verificationCode: code, // In production, send via email/SMS
      email: email,
      role: role
    });
  } catch (err) {
    console.error('Signup error:', err);
    res.status(500).json({ error: 'Server error during signup.' });
  }
};

// POST /api/auth/login
exports.login = async (req, res) => {
  try {
    const { email, password, role } = req.body;

    // Find user
    const [users] = await pool.query('SELECT * FROM users WHERE email = ? AND role = ?', [email, role]);
    if (users.length === 0) {
      return res.status(400).json({ error: 'Invalid email, role, or password.' });
    }

    const user = users[0];

    // Check if user is active
    if (user.status !== 'active') {
      return res.status(403).json({ error: 'Your account has been suspended or deactivated.' });
    }

    // Verify password
    const isMatch = await bcrypt.compare(password, user.password);
    if (!isMatch) {
      return res.status(400).json({ error: 'Invalid email, role, or password.' });
    }

    // Generate verification code
    const code = generateCode();
    const expiresAt = new Date(Date.now() + 10 * 60 * 1000);

    await pool.query('DELETE FROM verification_codes WHERE email = ?', [email]);
    await pool.query(
      'INSERT INTO verification_codes (email, code, expires_at) VALUES (?, ?, ?)',
      [email, code, expiresAt]
    );

    res.json({
      message: 'Verification code sent.',
      verificationCode: code, // In production, send via email/SMS
      email: email,
      role: role,
      userName: user.name
    });
  } catch (err) {
    console.error('Login error:', err);
    res.status(500).json({ error: 'Server error during login.' });
  }
};

// POST /api/auth/verify
exports.verify = async (req, res) => {
  try {
    const { email, code } = req.body;

    // Find valid code
    const [codes] = await pool.query(
      'SELECT * FROM verification_codes WHERE email = ? AND code = ? AND used = 0 AND expires_at > NOW() ORDER BY created_at DESC LIMIT 1',
      [email, code]
    );

    if (codes.length === 0) {
      return res.status(400).json({ error: 'Invalid or expired verification code.' });
    }

    // Mark code as used
    await pool.query('UPDATE verification_codes SET used = 1 WHERE id = ?', [codes[0].id]);

    // Mark user as verified
    await pool.query('UPDATE users SET is_verified = 1 WHERE email = ?', [email]);

    // Get user data
    const [users] = await pool.query('SELECT id, name, email, role, phone, location, business_name, bio, profile_photo, status FROM users WHERE email = ?', [email]);
    const user = users[0];

    // Generate JWT
    const token = generateToken(user);

    res.json({
      message: 'Verification successful!',
      token,
      user: {
        id: user.id,
        name: user.name,
        email: user.email,
        role: user.role,
        phone: user.phone,
        location: user.location,
        businessName: user.business_name,
        bio: user.bio,
        profilePhoto: user.profile_photo,
        status: user.status
      }
    });
  } catch (err) {
    console.error('Verify error:', err);
    res.status(500).json({ error: 'Server error during verification.' });
  }
};

// POST /api/auth/resend-code
exports.resendCode = async (req, res) => {
  try {
    const { email } = req.body;

    // Check user exists
    const [users] = await pool.query('SELECT id FROM users WHERE email = ?', [email]);
    if (users.length === 0) {
      return res.status(400).json({ error: 'User not found.' });
    }

    // Generate new code
    const code = generateCode();
    const expiresAt = new Date(Date.now() + 10 * 60 * 1000);

    await pool.query('DELETE FROM verification_codes WHERE email = ?', [email]);
    await pool.query(
      'INSERT INTO verification_codes (email, code, expires_at) VALUES (?, ?, ?)',
      [email, code, expiresAt]
    );

    res.json({
      message: 'New verification code generated.',
      verificationCode: code
    });
  } catch (err) {
    console.error('Resend code error:', err);
    res.status(500).json({ error: 'Server error.' });
  }
};

// GET /api/auth/me
exports.getProfile = async (req, res) => {
  try {
    const [users] = await pool.query(
      'SELECT id, name, email, role, phone, location, business_name, bio, profile_photo, status, created_at FROM users WHERE id = ?',
      [req.user.id]
    );

    if (users.length === 0) {
      return res.status(404).json({ error: 'User not found.' });
    }

    const user = users[0];
    res.json({
      id: user.id,
      name: user.name,
      email: user.email,
      role: user.role,
      phone: user.phone,
      location: user.location,
      businessName: user.business_name,
      bio: user.bio,
      profilePhoto: user.profile_photo,
      status: user.status,
      createdAt: user.created_at
    });
  } catch (err) {
    console.error('Get profile error:', err);
    res.status(500).json({ error: 'Server error.' });
  }
};

// PUT /api/auth/profile
exports.updateProfile = async (req, res) => {
  try {
    const { name, phone, location, businessName, bio } = req.body;

    await pool.query(
      'UPDATE users SET name = ?, phone = ?, location = ?, business_name = ?, bio = ? WHERE id = ?',
      [name, phone || null, location || null, businessName || null, bio || null, req.user.id]
    );

    // Return updated user
    const [users] = await pool.query(
      'SELECT id, name, email, role, phone, location, business_name, bio, profile_photo, status FROM users WHERE id = ?',
      [req.user.id]
    );
    const user = users[0];

    res.json({
      message: 'Profile updated successfully.',
      user: {
        id: user.id,
        name: user.name,
        email: user.email,
        role: user.role,
        phone: user.phone,
        location: user.location,
        businessName: user.business_name,
        bio: user.bio,
        profilePhoto: user.profile_photo,
        status: user.status
      }
    });
  } catch (err) {
    console.error('Update profile error:', err);
    res.status(500).json({ error: 'Server error.' });
  }
};
