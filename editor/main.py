#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
ClawWorld 游戏编辑器
主程序入口
"""

import tkinter as tk
from tkinter import ttk, messagebox
import os
import sys

# 设置数据目录的相对路径
DATA_DIR = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'data')

class GameEditor:
    def __init__(self, root):
        self.root = root
        self.root.title("ClawWorld 游戏编辑器")
        self.root.geometry("1200x800")

        # 创建主框架
        self.create_menu()
        self.create_notebook()

    def create_menu(self):
        """创建菜单栏"""
        menubar = tk.Menu(self.root)

        # 文件菜单
        file_menu = tk.Menu(menubar, tearoff=0)
        file_menu.add_command(label="刷新所有数据", command=self.refresh_all)
        file_menu.add_separator()
        file_menu.add_command(label="退出", command=self.root.quit)
        menubar.add_cascade(label="文件", menu=file_menu)

        # 帮助菜单
        help_menu = tk.Menu(menubar, tearoff=0)
        help_menu.add_command(label="关于", command=self.show_about)
        menubar.add_cascade(label="帮助", menu=help_menu)

        self.root.config(menu=menubar)

    def create_notebook(self):
        """创建标签页"""
        self.notebook = ttk.Notebook(self.root)
        self.notebook.pack(fill=tk.BOTH, expand=True, padx=5, pady=5)

        # 导入各个编辑器模块
        from map_editor import MapEditor
        from enemy_editor import EnemyEditor
        from item_editor import ItemEditor
        from equipment_editor import EquipmentEditor
        from npc_editor import NPCEditor
        from skill_editor import SkillEditor
        from role_editor import RoleEditor
        from chest_editor import ChestEditor
        from gift_editor import GiftEditor
        from terrain_editor import TerrainEditor

        # 创建各个编辑器标签页
        self.map_editor = MapEditor(self.notebook)
        self.notebook.add(self.map_editor.frame, text="地图编辑器")

        self.enemy_editor = EnemyEditor(self.notebook)
        self.notebook.add(self.enemy_editor.frame, text="敌人编辑器")

        self.item_editor = ItemEditor(self.notebook)
        self.notebook.add(self.item_editor.frame, text="物品编辑器")

        self.equipment_editor = EquipmentEditor(self.notebook)
        self.notebook.add(self.equipment_editor.frame, text="装备编辑器")

        self.npc_editor = NPCEditor(self.notebook)
        self.notebook.add(self.npc_editor.frame, text="NPC编辑器")

        self.chest_editor = ChestEditor(self.notebook)
        self.notebook.add(self.chest_editor.frame, text="宝箱编辑器")

        self.gift_editor = GiftEditor(self.notebook)
        self.notebook.add(self.gift_editor.frame, text="礼包编辑器")

        self.skill_editor = SkillEditor(self.notebook)
        self.notebook.add(self.skill_editor.frame, text="技能编辑器")

        self.role_editor = RoleEditor(self.notebook)
        self.notebook.add(self.role_editor.frame, text="职业编辑器")

        self.terrain_editor = TerrainEditor(self.notebook)
        self.notebook.add(self.terrain_editor.frame, text="地形编辑器")

    def refresh_all(self):
        """刷新所有数据"""
        self.map_editor.load_data()
        self.enemy_editor.load_data()
        self.item_editor.load_data()
        self.equipment_editor.load_data()
        self.npc_editor.load_data()
        self.chest_editor.load_data()
        self.gift_editor.load_data()
        self.skill_editor.load_data()
        self.role_editor.load_data()
        self.terrain_editor.load_data()
        messagebox.showinfo("提示", "所有数据已刷新")

    def show_about(self):
        """显示关于对话框"""
        messagebox.showinfo("关于", "ClawWorld 游戏编辑器\n版本 1.0\n\n用于编辑游戏数据的可视化工具")


def main():
    root = tk.Tk()
    app = GameEditor(root)
    root.mainloop()


if __name__ == "__main__":
    main()
