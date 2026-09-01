# -*- coding: utf-8 -*-
"""MyCloud: a minimal personal file-storage web app."""
import os

from flask import Flask, abort, flash, redirect, render_template, request, send_from_directory, url_for
from werkzeug.utils import secure_filename

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
STORAGE_DIR = os.path.join(BASE_DIR, "storage")
MAX_CONTENT_LENGTH = 100 * 1024 * 1024  # 100 MB per upload

app = Flask(__name__)
app.config["MAX_CONTENT_LENGTH"] = MAX_CONTENT_LENGTH
app.secret_key = os.environ.get("MYCLOUD_SECRET_KEY", "dev-secret-key")

os.makedirs(STORAGE_DIR, exist_ok=True)


def human_size(num_bytes):
    for unit in ("B", "KB", "MB", "GB"):
        if num_bytes < 1024:
            return f"{num_bytes:.0f} {unit}" if unit == "B" else f"{num_bytes:.1f} {unit}"
        num_bytes /= 1024
    return f"{num_bytes:.1f} TB"


def list_files():
    files = []
    for name in sorted(os.listdir(STORAGE_DIR)):
        path = os.path.join(STORAGE_DIR, name)
        if os.path.isfile(path):
            files.append({"name": name, "size": human_size(os.path.getsize(path))})
    return files


@app.route("/")
def index():
    return render_template("index.html", files=list_files())


@app.route("/upload", methods=["POST"])
def upload():
    uploaded = request.files.get("file")
    if not uploaded or uploaded.filename == "":
        flash("No file selected.")
        return redirect(url_for("index"))

    filename = secure_filename(uploaded.filename)
    if not filename:
        flash("Invalid file name.")
        return redirect(url_for("index"))

    uploaded.save(os.path.join(STORAGE_DIR, filename))
    flash(f"Uploaded {filename}.")
    return redirect(url_for("index"))


@app.route("/download/<path:filename>")
def download(filename):
    filename = secure_filename(filename)
    if not os.path.isfile(os.path.join(STORAGE_DIR, filename)):
        abort(404)
    return send_from_directory(STORAGE_DIR, filename, as_attachment=True)


@app.route("/delete/<path:filename>", methods=["POST"])
def delete(filename):
    filename = secure_filename(filename)
    path = os.path.join(STORAGE_DIR, filename)
    if os.path.isfile(path):
        os.remove(path)
        flash(f"Deleted {filename}.")
    else:
        abort(404)
    return redirect(url_for("index"))


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=True)
