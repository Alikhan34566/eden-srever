// server.js
import express from 'express';
import multer from 'multer';
import sharp from 'sharp';
import { Low } from 'lowdb';
import { JSONFile } from 'lowdb/node';
import cors from 'cors';
import path from 'path';
import fs from 'fs';

const __dirname = path.resolve();
const app = express();
app.use(cors());
app.use(express.json());
app.use('/uploads', express.static('uploads'));
// отдаём статику из корня (index.html / admin.html / css / js)
app.use(express.static('.'));

const adapter = new JSONFile('db.json');
const db = new Low(adapter);
await db.read();
db.data ||= { dishes: [] };

const storage = multer.diskStorage({
  destination: (_, __, cb) => {
    if (!fs.existsSync('uploads')) fs.mkdirSync('uploads');
    cb(null, 'uploads');
  },
  filename: (_, file, cb) => cb(null, Date.now() + path.extname(file.originalname))
});
const upload = multer({ storage });

async function compress(file) {
  const out = file.path.replace(/\.\w+$/, '.webp');
  await sharp(file.path)
    .resize(600, 600, { fit: 'inside' })
    .toFormat('webp')
    .toFile(out);
  fs.unlinkSync(file.path);
  return out;
}

// CRUD
app.get('/api/dishes', (_, res) => res.json(db.data.dishes));

app.post('/api/dishes', upload.single('img'), async (req, res) => {
  const imgPath = await compress(req.file);
  const dish = {
    id: Date.now(),
    name: req.body.name,
    cat: req.body.cat,
    price: Number(req.body.price),
    kcal: Number(req.body.kcal),
    ingr: req.body.ingr,
    desc: req.body.desc,
    img: '/' + imgPath.replace(/\\/g, '/')
  };
  db.data.dishes.push(dish);
  await db.write();
  res.json(dish);
});

app.delete('/api/dishes/:id', async (req, res) => {
  const id = +req.params.id;
  const dish = db.data.dishes.find(d => d.id === id);
  if (!dish) return res.sendStatus(404);
  if (fs.existsSync(dish.img.slice(1))) fs.unlinkSync(dish.img.slice(1));
  db.data.dishes = db.data.dishes.filter(d => d.id !== id);
  await db.write();
  res.sendStatus(204);
});

const PORT = process.env.PORT || 4000;
app.listen(PORT, () => console.log(`🚀  http://localhost:${PORT}`));
