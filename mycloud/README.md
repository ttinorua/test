# MyCloud

A minimal personal file-storage web app: upload, list, download, and delete files
through a browser.

## Run

```bash
cd mycloud
pip install -r requirements.txt
python app.py
```

Then open http://localhost:5000. Uploaded files are stored on disk under
`mycloud/storage/` (created automatically, not committed to git).
