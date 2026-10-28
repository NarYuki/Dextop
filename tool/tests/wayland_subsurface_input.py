"""Compile and exercise the production subsurface hit test on a Wayland dev host.
Usage: python3 wayland_subsurface_input.py /path/to/compositor.c
"""
from pathlib import Path
import subprocess
import sys
import tempfile
source = Path(sys.argv[1]).read_text()
a = source.index('static struct surface *surface_at(')
b = source.index('static void handle_pointer(', a)
header = '#include <wayland-util.h>\n#include <stdint.h>\n#include <stdbool.h>\n#include <stddef.h>\n#include <assert.h>\n#include <stdio.h>\nstruct pointer_rect {int64_t x1,y1,x2,y2;};\nstruct pointer_region {struct pointer_rect *rects;size_t count;};\nstruct surface {struct wl_list children,child_link;int32_t sub_x,sub_y;bool below_parent,input_set;struct pointer_region input;int width,height;};\nstatic int surface_width(struct surface*s){return s->width;}\nstatic int surface_height(struct surface*s){return s->height;}\n'
cases = 'static void init(struct surface*s,int w,int h){s->width=w;s->height=h;wl_list_init(&s->children);wl_list_init(&s->child_link);}\nint main(){struct surface root={0},bubble={0},nested={0},below={0};init(&root,800,600);init(&bubble,300,200);init(&nested,50,40);init(&below,800,600);bubble.sub_x=450;bubble.sub_y=30;nested.sub_x=20;nested.sub_y=15;below.below_parent=true;wl_list_insert(root.children.prev,&below.child_link);wl_list_insert(root.children.prev,&bubble.child_link);wl_list_insert(bubble.children.prev,&nested.child_link);int32_t x,y;\nassert(surface_at(&root,460,50,&x,&y,0)==&bubble&&x==10&&y==20);\nassert(surface_at(&root,480,50,&x,&y,0)==&nested&&x==10&&y==5);\nbubble.input_set=true;assert(surface_at(&root,460,50,&x,&y,0)==&root&&x==460&&y==50);\nstruct pointer_rect r={0,0,100,100};bubble.input.rects=&r;bubble.input.count=1;\nassert(surface_at(&root,460,50,&x,&y,0)==&bubble);\nassert(surface_at(&root,600,50,&x,&y,0)==&root);\nroot.input_set=true;assert(surface_at(&root,100,100,&x,&y,0)==&below);\nassert(surface_at(&root,900,900,&x,&y,0)==NULL);\nputs("SUBSURFACE_POINTER_COORDINATES_STACKING_AND_INPUT_REGIONS_OK");}\n'
with tempfile.TemporaryDirectory(prefix='dextop-input-test-') as directory:
    root = Path(directory)
    code = root / 'probe.c'
    code.write_text(header + source[a:b] + cases)
    subprocess.run(['cc', str(code), '-o', str(root / 'probe'), '-lwayland-client'], check=True)
    subprocess.run([str(root / 'probe')], check=True)
