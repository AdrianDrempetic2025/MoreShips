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
        # Bake the WHOLE model (all cubes). Faces the artist left untextured
        # (#missing) get the artist's texture too — one rigid client-rendered
        # model. Hull material is shown in-game by orbiting defense blocks.
        # The artist's Blockbench "display" settings must NOT ship — display
        # entities render raw.
        whole = {
            "credit": m.get("credit", ""),
            "texture_size": [64, 64],
            "textures": {"0": tex_ref, "particle": tex_ref},
            "elements": [],
        }
        xs, ys, zs = [], [], []
        for el in m["elements"]:
            for face in el.get("faces", {}).values():
                if face.get("texture") in (None, "#missing"):
                    face["texture"] = "#0"
            whole["elements"].append(el)
            xs += [el["from"][0], el["to"][0]]
            ys += [el["from"][1], el["to"][1]]
            zs += [el["from"][2], el["to"][2]]
        cx, cy, cz = (min(xs)+max(xs))/2, (min(ys)+max(ys))/2, (min(zs)+max(zs))/2
        # WORN-MODEL placement: the ship model is the controller stand's HELMET.
        # Scale 2 = ship renders at hull size (worn models render at 1:1 px,
        # which reads half-size in world). Translation maps the model's deck
        # FLOOR (bbox ymin) onto the stand's HEAD height — the rider sits in
        # the boat with the floor at seat level, hull hanging down into the
        # water. Position/rotation come from the stand itself.
        ymin = min(ys)
        sc = 2
        whole["display"] = {"head": {
            "translation": [round(sc * (8 - cx), 3), round(sc * (8 - ymin), 3), round(sc * (8 - cz), 3)],
            "rotation": [0, 0, 0],
            "scale": [sc, sc, sc],
        }}
        trim = whole
        if not trim["elements"]:
            print(f"skip {size}: no textured cubes")
            continue
        json.dump(trim, open(os.path.join(
            pack, "assets/moreships/models/item", f"{item_model}.json"), "w"), indent=1)
        shutil.copy(os.path.join(ROOT, "assets", size, texture),
                    os.path.join(pack, "assets/moreships/textures/item", texture))
        # 26.x requires the namespaced type "minecraft:model" — bare "model"
        # (the 1.21.4 shorthand) silently fails to resolve client-side
        json.dump({"model": {"type": "minecraft:model", "model": f"moreships:item/{item_model}"}},
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
