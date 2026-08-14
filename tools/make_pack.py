#!/usr/bin/env python3
"""Regenerates the MoreShips resource pack from assets/ into build/libs.

Model convention (per artist): cubes WITHOUT texture (#missing in exported
json) are hull-material cubes -> excluded here, rendered in-game as
BlockDisplays with the live hull material. Textured cubes go into the trim
model. pack.mcmeta uses min_format/max_format (required since 25w31a;
MC 26.2 = format 84).
"""
import json, os, shutil, sys, zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SIZES = {
    "small": ("small_ship_hull.json", "small_ship_metal_coat.png", "ship_small_trim"),
    # add medium/large here as models arrive
}

def main():
    pack = os.path.join(ROOT, "build", "pack")
    if os.path.isdir(pack):
        shutil.rmtree(pack)
    for sub in ("assets/moreships/models/item", "assets/moreships/textures/item",
                "assets/moreships/items"):
        os.makedirs(os.path.join(pack, sub), exist_ok=True)
    json.dump({"pack": {"min_format": 84, "max_format": 99,
                        "description": "MoreShips custom hull models"}},
              open(os.path.join(pack, "pack.mcmeta"), "w"))

    for size, (model, texture, item_model) in SIZES.items():
        src = os.path.join(ROOT, "assets", size, model)
        if not os.path.exists(src):
            print(f"skip {size}: {src} missing")
            continue
        m = json.load(open(src, encoding="utf-8"))
        tex_ref = f"moreships:item/{os.path.splitext(texture)[0]}"
        # NOTE: the artist's Blockbench "display" settings (GUI/hand/head
        # transforms, often scale 0.1-0.5) must NOT ship — an ItemDisplay would
        # render the model at that tiny scale. Display entities render raw.
        trim = {
            "credit": m.get("credit", ""),
            "texture_size": [64, 64],
            "textures": {"0": tex_ref, "particle": tex_ref},
            "elements": [el for el in m["elements"]
                         if any(f.get("texture") not in (None, "#missing")
                                for f in el.get("faces", {}).values())],
        }
        if not trim["elements"]:
            print(f"skip {size}: no textured cubes")
            continue
        json.dump(trim, open(os.path.join(
            pack, "assets/moreships/models/item", f"{item_model}.json"), "w"), indent=1)
        shutil.copy(os.path.join(ROOT, "assets", size, texture),
                    os.path.join(pack, "assets/moreships/textures/item", texture))
        json.dump({"model": {"type": "model", "model": f"moreships:item/{item_model}"}},
                  open(os.path.join(pack, "assets/moreships/items", f"{item_model}.json"), "w"))
        print(f"{size}: {len(trim['elements'])} trim cubes")

    out = os.path.join(ROOT, "build", "libs", "MoreShips-pack.zip")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    if os.path.exists(out):
        os.remove(out)
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for root, _, files in os.walk(pack):
            for f in files:
                full = os.path.join(root, f)
                z.write(full, os.path.relpath(full, pack))
    print("pack:", out, os.path.getsize(out), "bytes")

if __name__ == "__main__":
    main()
