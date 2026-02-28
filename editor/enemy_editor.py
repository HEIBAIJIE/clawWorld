#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
敌人编辑器
"""

import tkinter as tk
from tkinter import ttk, messagebox
from csv_utils import read_csv, write_csv, get_fieldnames
from constants import ENEMY_TIERS, RARITIES


class EnemyEditor:
    def __init__(self, parent):
        self.frame = ttk.Frame(parent)
        self.current_enemy = None

        self.create_widgets()
        self.load_data()

    def create_widgets(self):
        """创建界面组件"""
        # 左侧 - 敌人列表
        left_panel = ttk.Frame(self.frame, width=200)
        left_panel.pack(side=tk.LEFT, fill=tk.Y, padx=5, pady=5)

        ttk.Label(left_panel, text="敌人列表").pack()

        list_frame = ttk.Frame(left_panel)
        list_frame.pack(fill=tk.BOTH, expand=True)

        self.enemy_listbox = tk.Listbox(list_frame, width=25)
        self.enemy_listbox.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        self.enemy_listbox.bind('<<ListboxSelect>>', self.on_enemy_select)

        scrollbar = ttk.Scrollbar(list_frame, orient=tk.VERTICAL,
                                  command=self.enemy_listbox.yview)
        scrollbar.pack(side=tk.RIGHT, fill=tk.Y)
        self.enemy_listbox.config(yscrollcommand=scrollbar.set)

        btn_frame = ttk.Frame(left_panel)
        btn_frame.pack(fill=tk.X, pady=5)

        ttk.Button(btn_frame, text="新建敌人", command=self.new_enemy).pack(fill=tk.X)
        ttk.Button(btn_frame, text="删除敌人", command=self.delete_enemy).pack(fill=tk.X)
        ttk.Button(btn_frame, text="保存所有", command=self.save_all).pack(fill=tk.X)

        # 右侧 - 编辑面板
        right_panel = ttk.Frame(self.frame)
        right_panel.pack(side=tk.LEFT, fill=tk.BOTH, expand=True, padx=5, pady=5)

        # 创建notebook用于分组
        self.notebook = ttk.Notebook(right_panel)
        self.notebook.pack(fill=tk.BOTH, expand=True)

        # 基本信息标签页
        self.create_basic_tab()

        # 属性标签页
        self.create_stats_tab()

        # 掉落物标签页
        self.create_loot_tab()

    def create_basic_tab(self):
        """创建基本信息标签页"""
        tab = ttk.Frame(self.notebook)
        self.notebook.add(tab, text="基本信息")

        self.basic_fields = {}
        labels = [
            ('id', '敌人ID'),
            ('name', '名称'),
            ('description', '描述'),
            ('level', '等级'),
            ('skills', '技能(分号分隔)'),
            ('expMin', '最小经验'),
            ('expMax', '最大经验'),
            ('goldMin', '最小金币'),
            ('goldMax', '最大金币'),
            ('respawnSeconds', '刷新时间(秒)'),
            ('walkSprite', '行走图'),
            ('portrait', '头像')
        ]

        for field, label in labels:
            row = ttk.Frame(tab)
            row.pack(fill=tk.X, padx=10, pady=2)
            ttk.Label(row, text=label, width=15).pack(side=tk.LEFT)
            entry = ttk.Entry(row)
            entry.pack(side=tk.LEFT, fill=tk.X, expand=True)
            self.basic_fields[field] = entry

        # 品阶选择
        tier_row = ttk.Frame(tab)
        tier_row.pack(fill=tk.X, padx=10, pady=2)
        ttk.Label(tier_row, text="品阶", width=15).pack(side=tk.LEFT)
        self.tier_var = tk.StringVar(value='NORMAL')
        tier_combo = ttk.Combobox(tier_row, textvariable=self.tier_var,
                                  values=ENEMY_TIERS, state='readonly')
        tier_combo.pack(side=tk.LEFT, fill=tk.X, expand=True)

    def create_stats_tab(self):
        """创建属性标签页"""
        tab = ttk.Frame(self.notebook)
        self.notebook.add(tab, text="属性")

        self.stat_fields = {}
        labels = [
            ('health', '生命值'),
            ('mana', '法力值'),
            ('physicalAttack', '物理攻击'),
            ('physicalDefense', '物理防御'),
            ('magicAttack', '法术攻击'),
            ('magicDefense', '法术防御'),
            ('speed', '速度'),
            ('critRate', '暴击率'),
            ('critDamage', '暴击伤害'),
            ('hitRate', '命中率'),
            ('dodgeRate', '闪避率')
        ]

        for field, label in labels:
            row = ttk.Frame(tab)
            row.pack(fill=tk.X, padx=10, pady=2)
            ttk.Label(row, text=label, width=15).pack(side=tk.LEFT)
            entry = ttk.Entry(row)
            entry.pack(side=tk.LEFT, fill=tk.X, expand=True)
            self.stat_fields[field] = entry

    def create_loot_tab(self):
        """创建掉落物标签页"""
        tab = ttk.Frame(self.notebook)
        self.notebook.add(tab, text="掉落物")

        # 掉落物列表
        list_frame = ttk.Frame(tab)
        list_frame.pack(fill=tk.BOTH, expand=True, padx=10, pady=5)

        columns = ('itemId', 'rarity', 'dropRate')
        self.loot_tree = ttk.Treeview(list_frame, columns=columns, show='headings')
        self.loot_tree.heading('itemId', text='物品ID')
        self.loot_tree.heading('rarity', text='稀有度')
        self.loot_tree.heading('dropRate', text='掉落率')
        self.loot_tree.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)

        scrollbar = ttk.Scrollbar(list_frame, orient=tk.VERTICAL,
                                  command=self.loot_tree.yview)
        scrollbar.pack(side=tk.RIGHT, fill=tk.Y)
        self.loot_tree.config(yscrollcommand=scrollbar.set)

        # 掉落物操作按钮
        btn_frame = ttk.Frame(tab)
        btn_frame.pack(fill=tk.X, padx=10, pady=5)

        ttk.Button(btn_frame, text="添加掉落物", command=self.add_loot).pack(side=tk.LEFT, padx=2)
        ttk.Button(btn_frame, text="编辑掉落物", command=self.edit_loot).pack(side=tk.LEFT, padx=2)
        ttk.Button(btn_frame, text="删除掉落物", command=self.delete_loot).pack(side=tk.LEFT, padx=2)

    def load_data(self):
        """加载数据"""
        self.enemies = read_csv('enemies.csv')
        self.loots = read_csv('enemy_loot.csv')
        self.items = read_csv('items.csv')
        self.equipment = read_csv('equipment.csv')

        self.refresh_enemy_list()

    def refresh_enemy_list(self):
        """刷新敌人列表"""
        self.enemy_listbox.delete(0, tk.END)
        for e in self.enemies:
            self.enemy_listbox.insert(tk.END, f"{e['id']} - {e['name']}")

    def on_enemy_select(self, event):
        """选择敌人"""
        selection = self.enemy_listbox.curselection()
        if selection:
            idx = selection[0]
            self.current_enemy = self.enemies[idx]
            self.load_enemy_data()

    def load_enemy_data(self):
        """加载敌人数据到表单"""
        if not self.current_enemy:
            return

        # 加载基本信息
        for field, entry in self.basic_fields.items():
            entry.delete(0, tk.END)
            entry.insert(0, self.current_enemy.get(field, ''))

        self.tier_var.set(self.current_enemy.get('tier', 'NORMAL'))

        # 加载属性
        for field, entry in self.stat_fields.items():
            entry.delete(0, tk.END)
            entry.insert(0, self.current_enemy.get(field, ''))

        # 加载掉落物
        self.refresh_loot_list()

    def refresh_loot_list(self):
        """刷新掉落物列表"""
        for item in self.loot_tree.get_children():
            self.loot_tree.delete(item)

        if not self.current_enemy:
            return

        enemy_id = self.current_enemy['id']
        for loot in self.loots:
            if loot['enemyId'] == enemy_id:
                self.loot_tree.insert('', tk.END, values=(
                    loot['itemId'], loot['rarity'], loot['dropRate']
                ))

    def save_current_enemy(self):
        """保存当前敌人数据"""
        if not self.current_enemy:
            return

        # 保存基本信息
        for field, entry in self.basic_fields.items():
            self.current_enemy[field] = entry.get()

        self.current_enemy['tier'] = self.tier_var.get()

        # 保存属性
        for field, entry in self.stat_fields.items():
            self.current_enemy[field] = entry.get()

        self.refresh_enemy_list()

    def new_enemy(self):
        """新建敌人"""
        dialog = tk.Toplevel(self.frame)
        dialog.title("新建敌人")
        dialog.geometry("300x150")

        ttk.Label(dialog, text="敌人ID:").pack(pady=5)
        id_entry = ttk.Entry(dialog)
        id_entry.pack(fill=tk.X, padx=20)

        ttk.Label(dialog, text="敌人名称:").pack(pady=5)
        name_entry = ttk.Entry(dialog)
        name_entry.pack(fill=tk.X, padx=20)

        def create():
            enemy_id = id_entry.get().strip()
            if not enemy_id:
                messagebox.showerror("错误", "请输入敌人ID")
                return

            for e in self.enemies:
                if e['id'] == enemy_id:
                    messagebox.showerror("错误", "敌人ID已存在")
                    return

            new_enemy = {
                'id': enemy_id,
                'name': name_entry.get(),
                'description': '',
                'level': '1',
                'tier': 'NORMAL',
                'health': '100',
                'mana': '50',
                'physicalAttack': '10',
                'physicalDefense': '5',
                'magicAttack': '5',
                'magicDefense': '5',
                'speed': '100',
                'critRate': '0.05',
                'critDamage': '1.5',
                'hitRate': '0.9',
                'dodgeRate': '0.05',
                'skills': 'normal_attack',
                'expMin': '10',
                'expMax': '15',
                'goldMin': '5',
                'goldMax': '10',
                'respawnSeconds': '60',
                'walkSprite': '',
                'portrait': ''
            }

            self.enemies.append(new_enemy)
            self.refresh_enemy_list()
            dialog.destroy()

        ttk.Button(dialog, text="创建", command=create).pack(pady=10)

    def delete_enemy(self):
        """删除敌人"""
        selection = self.enemy_listbox.curselection()
        if not selection:
            messagebox.showwarning("警告", "请先选择一个敌人")
            return

        idx = selection[0]
        enemy = self.enemies[idx]

        if not messagebox.askyesno("确认", f"确定要删除敌人 {enemy['id']} 吗？"):
            return

        # 删除敌人
        del self.enemies[idx]

        # 删除相关掉落物
        self.loots = [l for l in self.loots if l['enemyId'] != enemy['id']]

        self.current_enemy = None
        self.refresh_enemy_list()

    def add_loot(self):
        """添加掉落物"""
        if not self.current_enemy:
            messagebox.showwarning("警告", "请先选择一个敌人")
            return

        self.edit_loot_dialog(None)

    def edit_loot(self):
        """编辑掉落物"""
        if not self.current_enemy:
            return

        selection = self.loot_tree.selection()
        if not selection:
            messagebox.showwarning("警告", "请先选择一个掉落物")
            return

        item = self.loot_tree.item(selection[0])
        values = item['values']
        self.edit_loot_dialog(values)

    def edit_loot_dialog(self, existing_values):
        """掉落物编辑对话框"""
        dialog = tk.Toplevel(self.frame)
        dialog.title("编辑掉落物" if existing_values else "添加掉落物")
        dialog.geometry("350x200")

        # 物品选择
        ttk.Label(dialog, text="物品:").pack(pady=5)
        item_var = tk.StringVar()
        all_items = [i['id'] for i in self.items] + [e['id'] for e in self.equipment]
        item_combo = ttk.Combobox(dialog, textvariable=item_var, values=all_items)
        item_combo.pack(fill=tk.X, padx=20)

        # 稀有度
        ttk.Label(dialog, text="稀有度:").pack(pady=5)
        rarity_var = tk.StringVar(value='COMMON')
        rarity_combo = ttk.Combobox(dialog, textvariable=rarity_var,
                                    values=RARITIES, state='readonly')
        rarity_combo.pack(fill=tk.X, padx=20)

        # 掉落率
        ttk.Label(dialog, text="掉落率 (0-1):").pack(pady=5)
        rate_entry = ttk.Entry(dialog)
        rate_entry.pack(fill=tk.X, padx=20)

        if existing_values:
            item_var.set(existing_values[0])
            rarity_var.set(existing_values[1])
            rate_entry.insert(0, existing_values[2])

        def save():
            item_id = item_var.get()
            if not item_id:
                messagebox.showerror("错误", "请选择物品")
                return

            enemy_id = self.current_enemy['id']

            # 如果是编辑，先删除旧的
            if existing_values:
                self.loots = [l for l in self.loots
                              if not (l['enemyId'] == enemy_id and l['itemId'] == existing_values[0])]

            self.loots.append({
                'enemyId': enemy_id,
                'itemId': item_id,
                'rarity': rarity_var.get(),
                'dropRate': rate_entry.get()
            })

            self.refresh_loot_list()
            dialog.destroy()

        ttk.Button(dialog, text="保存", command=save).pack(pady=10)

    def delete_loot(self):
        """删除掉落物"""
        if not self.current_enemy:
            return

        selection = self.loot_tree.selection()
        if not selection:
            messagebox.showwarning("警告", "请先选择一个掉落物")
            return

        item = self.loot_tree.item(selection[0])
        item_id = item['values'][0]
        enemy_id = self.current_enemy['id']

        self.loots = [l for l in self.loots
                      if not (l['enemyId'] == enemy_id and l['itemId'] == item_id)]

        self.refresh_loot_list()

    def save_all(self):
        """保存所有数据"""
        self.save_current_enemy()

        # 保存敌人
        fieldnames = get_fieldnames('enemies.csv')
        if not fieldnames:
            fieldnames = ['id', 'name', 'description', 'level', 'tier',
                          'health', 'mana', 'physicalAttack', 'physicalDefense',
                          'magicAttack', 'magicDefense', 'speed', 'critRate',
                          'critDamage', 'hitRate', 'dodgeRate', 'skills',
                          'expMin', 'expMax', 'goldMin', 'goldMax', 'respawnSeconds',
                          'walkSprite', 'portrait']
        write_csv('enemies.csv', self.enemies, fieldnames)

        # 保存掉落物
        fieldnames = get_fieldnames('enemy_loot.csv')
        if not fieldnames:
            fieldnames = ['enemyId', 'itemId', 'rarity', 'dropRate']
        write_csv('enemy_loot.csv', self.loots, fieldnames)

        messagebox.showinfo("提示", "敌人数据已保存")
