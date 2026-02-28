#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
职业编辑器
"""

import tkinter as tk
from tkinter import ttk, messagebox
from csv_utils import read_csv, write_csv, get_fieldnames
from constants import ROLES


class RoleEditor:
    def __init__(self, parent):
        self.frame = ttk.Frame(parent)
        self.current_role = None

        self.create_widgets()
        self.load_data()

    def create_widgets(self):
        """创建界面组件"""
        # 左侧 - 职业列表
        left_panel = ttk.Frame(self.frame, width=200)
        left_panel.pack(side=tk.LEFT, fill=tk.Y, padx=5, pady=5)

        ttk.Label(left_panel, text="职业列表").pack()

        list_frame = ttk.Frame(left_panel)
        list_frame.pack(fill=tk.BOTH, expand=True)

        self.role_listbox = tk.Listbox(list_frame, width=25)
        self.role_listbox.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        self.role_listbox.bind('<<ListboxSelect>>', self.on_role_select)

        scrollbar = ttk.Scrollbar(list_frame, orient=tk.VERTICAL,
                                  command=self.role_listbox.yview)
        scrollbar.pack(side=tk.RIGHT, fill=tk.Y)
        self.role_listbox.config(yscrollcommand=scrollbar.set)

        btn_frame = ttk.Frame(left_panel)
        btn_frame.pack(fill=tk.X, pady=5)

        ttk.Button(btn_frame, text="新建职业", command=self.new_role).pack(fill=tk.X)
        ttk.Button(btn_frame, text="删除职业", command=self.delete_role).pack(fill=tk.X)
        ttk.Button(btn_frame, text="保存所有", command=self.save_all).pack(fill=tk.X)

        # 右侧 - 编辑面板
        right_panel = ttk.Frame(self.frame)
        right_panel.pack(side=tk.LEFT, fill=tk.BOTH, expand=True, padx=5, pady=5)

        # 创建notebook
        self.notebook = ttk.Notebook(right_panel)
        self.notebook.pack(fill=tk.BOTH, expand=True)

        self.create_basic_tab()
        self.create_stats_tab()
        self.create_skills_tab()

    def create_basic_tab(self):
        """创建基本信息标签页"""
        tab = ttk.Frame(self.notebook)
        self.notebook.add(tab, text="基本信息")

        self.basic_fields = {}
        labels = [
            ('id', '职业ID'),
            ('name', '名称'),
            ('description', '描述'),
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

    def create_stats_tab(self):
        """创建属性标签页"""
        tab = ttk.Frame(self.notebook)
        self.notebook.add(tab, text="基础属性")

        # 使用canvas实现滚动
        canvas = tk.Canvas(tab)
        scrollbar = ttk.Scrollbar(tab, orient=tk.VERTICAL, command=canvas.yview)
        scrollable_frame = ttk.Frame(canvas)

        scrollable_frame.bind(
            "<Configure>",
            lambda e: canvas.configure(scrollregion=canvas.bbox("all"))
        )

        canvas.create_window((0, 0), window=scrollable_frame, anchor="nw")
        canvas.configure(yscrollcommand=scrollbar.set)

        self.stat_fields = {}
        labels = [
            ('baseHealth', '基础生命'),
            ('baseMana', '基础法力'),
            ('basePhysicalAttack', '基础物攻'),
            ('basePhysicalDefense', '基础物防'),
            ('baseMagicAttack', '基础法攻'),
            ('baseMagicDefense', '基础法防'),
            ('baseSpeed', '基础速度'),
            ('baseCritRate', '基础暴击率'),
            ('baseCritDamage', '基础暴击伤害'),
            ('baseHitRate', '基础命中率'),
            ('baseDodgeRate', '基础闪避率'),
            ('healthPerLevel', '每级生命'),
            ('manaPerLevel', '每级法力'),
            ('physicalAttackPerLevel', '每级物攻'),
            ('physicalDefensePerLevel', '每级物防'),
            ('magicAttackPerLevel', '每级法攻'),
            ('magicDefensePerLevel', '每级法防'),
            ('speedPerLevel', '每级速度')
        ]

        for field, label in labels:
            row = ttk.Frame(scrollable_frame)
            row.pack(fill=tk.X, padx=10, pady=2)
            ttk.Label(row, text=label, width=15).pack(side=tk.LEFT)
            entry = ttk.Entry(row)
            entry.pack(side=tk.LEFT, fill=tk.X, expand=True)
            self.stat_fields[field] = entry

        canvas.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        scrollbar.pack(side=tk.RIGHT, fill=tk.Y)

    def create_skills_tab(self):
        """创建技能标签页"""
        tab = ttk.Frame(self.notebook)
        self.notebook.add(tab, text="职业技能")

        # 技能列表
        list_frame = ttk.Frame(tab)
        list_frame.pack(fill=tk.BOTH, expand=True, padx=10, pady=5)

        columns = ('skillId', 'learnLevel')
        self.skill_tree = ttk.Treeview(list_frame, columns=columns, show='headings')
        self.skill_tree.heading('skillId', text='技能ID')
        self.skill_tree.heading('learnLevel', text='学习等级')
        self.skill_tree.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)

        scrollbar = ttk.Scrollbar(list_frame, orient=tk.VERTICAL,
                                  command=self.skill_tree.yview)
        scrollbar.pack(side=tk.RIGHT, fill=tk.Y)
        self.skill_tree.config(yscrollcommand=scrollbar.set)

        # 操作按钮
        btn_frame = ttk.Frame(tab)
        btn_frame.pack(fill=tk.X, padx=10, pady=5)

        ttk.Button(btn_frame, text="添加技能", command=self.add_role_skill).pack(side=tk.LEFT, padx=2)
        ttk.Button(btn_frame, text="编辑技能", command=self.edit_role_skill).pack(side=tk.LEFT, padx=2)
        ttk.Button(btn_frame, text="删除技能", command=self.delete_role_skill).pack(side=tk.LEFT, padx=2)

    def load_data(self):
        """加载数据"""
        self.roles = read_csv('roles.csv')
        self.role_skills = read_csv('role_skills.csv')
        self.skills = read_csv('skills.csv')

        self.refresh_role_list()

    def refresh_role_list(self):
        """刷新职业列表"""
        self.role_listbox.delete(0, tk.END)
        for role in self.roles:
            self.role_listbox.insert(tk.END, f"{role['id']} - {role['name']}")

    def on_role_select(self, event):
        """选择职业"""
        selection = self.role_listbox.curselection()
        if selection:
            idx = selection[0]
            self.current_role = self.roles[idx]
            self.load_role_data()

    def load_role_data(self):
        """加载职业数据到表单"""
        if not self.current_role:
            return

        for field, entry in self.basic_fields.items():
            entry.delete(0, tk.END)
            entry.insert(0, self.current_role.get(field, ''))

        for field, entry in self.stat_fields.items():
            entry.delete(0, tk.END)
            entry.insert(0, self.current_role.get(field, '0'))

        self.refresh_skill_list()

    def refresh_skill_list(self):
        """刷新技能列表"""
        for item in self.skill_tree.get_children():
            self.skill_tree.delete(item)

        if not self.current_role:
            return

        role_id = self.current_role['id']
        for rs in self.role_skills:
            if rs['roleId'] == role_id:
                self.skill_tree.insert('', tk.END, values=(
                    rs['skillId'], rs['learnLevel']
                ))

    def save_current_role(self):
        """保存当前职业"""
        if not self.current_role:
            return

        for field, entry in self.basic_fields.items():
            self.current_role[field] = entry.get()

        for field, entry in self.stat_fields.items():
            self.current_role[field] = entry.get()

        self.refresh_role_list()

    def new_role(self):
        """新建职业"""
        dialog = tk.Toplevel(self.frame)
        dialog.title("新建职业")
        dialog.geometry("300x150")

        ttk.Label(dialog, text="职业ID:").pack(pady=5)
        id_entry = ttk.Entry(dialog)
        id_entry.pack(fill=tk.X, padx=20)

        ttk.Label(dialog, text="职业名称:").pack(pady=5)
        name_entry = ttk.Entry(dialog)
        name_entry.pack(fill=tk.X, padx=20)

        def create():
            role_id = id_entry.get().strip()
            if not role_id:
                messagebox.showerror("错误", "请输入职业ID")
                return

            for role in self.roles:
                if role['id'] == role_id:
                    messagebox.showerror("错误", "职业ID已存在")
                    return

            new_role = {
                'id': role_id,
                'name': name_entry.get(),
                'description': '',
                'walkSprite': '',
                'portrait': '',
                'baseHealth': '100', 'baseMana': '50',
                'basePhysicalAttack': '10', 'basePhysicalDefense': '5',
                'baseMagicAttack': '5', 'baseMagicDefense': '5',
                'baseSpeed': '100', 'baseCritRate': '0.05',
                'baseCritDamage': '1.5', 'baseHitRate': '0.9', 'baseDodgeRate': '0.05',
                'healthPerLevel': '10', 'manaPerLevel': '5',
                'physicalAttackPerLevel': '2', 'physicalDefensePerLevel': '1',
                'magicAttackPerLevel': '1', 'magicDefensePerLevel': '1',
                'speedPerLevel': '1'
            }

            self.roles.append(new_role)
            self.refresh_role_list()
            dialog.destroy()

        ttk.Button(dialog, text="创建", command=create).pack(pady=10)

    def delete_role(self):
        """删除职业"""
        selection = self.role_listbox.curselection()
        if not selection:
            messagebox.showwarning("警告", "请先选择一个职业")
            return

        idx = selection[0]
        role = self.roles[idx]

        if not messagebox.askyesno("确认", f"确定要删除职业 {role['id']} 吗？"):
            return

        # 删除职业
        del self.roles[idx]

        # 删除相关技能
        self.role_skills = [rs for rs in self.role_skills if rs['roleId'] != role['id']]

        self.current_role = None
        self.refresh_role_list()

    def add_role_skill(self):
        """添加职业技能"""
        if not self.current_role:
            messagebox.showwarning("警告", "请先选择一个职业")
            return

        self.edit_role_skill_dialog(None)

    def edit_role_skill(self):
        """编辑职业技能"""
        if not self.current_role:
            return

        selection = self.skill_tree.selection()
        if not selection:
            messagebox.showwarning("警告", "请先选择一个技能")
            return

        item = self.skill_tree.item(selection[0])
        values = item['values']
        self.edit_role_skill_dialog(values)

    def edit_role_skill_dialog(self, existing_values):
        """职业技能编辑对话框"""
        dialog = tk.Toplevel(self.frame)
        dialog.title("编辑职业技能" if existing_values else "添加职业技能")
        dialog.geometry("350x150")

        # 技能选择
        ttk.Label(dialog, text="技能:").pack(pady=5)
        skill_var = tk.StringVar()
        skill_ids = [s['id'] for s in self.skills]
        skill_combo = ttk.Combobox(dialog, textvariable=skill_var, values=skill_ids)
        skill_combo.pack(fill=tk.X, padx=20)

        # 学习等级
        ttk.Label(dialog, text="学习等级:").pack(pady=5)
        level_entry = ttk.Entry(dialog)
        level_entry.pack(fill=tk.X, padx=20)

        if existing_values:
            skill_var.set(existing_values[0])
            level_entry.insert(0, existing_values[1])

        def save():
            skill_id = skill_var.get()
            if not skill_id:
                messagebox.showerror("错误", "请选择技能")
                return

            role_id = self.current_role['id']

            # 如果是编辑，先删除旧的
            if existing_values:
                self.role_skills = [rs for rs in self.role_skills
                                    if not (rs['roleId'] == role_id and rs['skillId'] == existing_values[0])]

            self.role_skills.append({
                'roleId': role_id,
                'skillId': skill_id,
                'learnLevel': level_entry.get()
            })

            self.refresh_skill_list()
            dialog.destroy()

        ttk.Button(dialog, text="保存", command=save).pack(pady=10)

    def delete_role_skill(self):
        """删除职业技能"""
        if not self.current_role:
            return

        selection = self.skill_tree.selection()
        if not selection:
            messagebox.showwarning("警告", "请先选择一个技能")
            return

        item = self.skill_tree.item(selection[0])
        skill_id = item['values'][0]
        role_id = self.current_role['id']

        self.role_skills = [rs for rs in self.role_skills
                           if not (rs['roleId'] == role_id and rs['skillId'] == skill_id)]

        self.refresh_skill_list()

    def save_all(self):
        """保存所有数据"""
        self.save_current_role()

        # 保存职业
        fieldnames = get_fieldnames('roles.csv')
        if not fieldnames:
            fieldnames = ['id', 'name', 'description',
                          'baseHealth', 'baseMana', 'basePhysicalAttack', 'basePhysicalDefense',
                          'baseMagicAttack', 'baseMagicDefense', 'baseSpeed',
                          'baseCritRate', 'baseCritDamage', 'baseHitRate', 'baseDodgeRate',
                          'healthPerLevel', 'manaPerLevel', 'physicalAttackPerLevel',
                          'physicalDefensePerLevel', 'magicAttackPerLevel', 'magicDefensePerLevel',
                          'speedPerLevel', 'critRatePerLevel', 'critDamagePerLevel',
                          'hitRatePerLevel', 'dodgeRatePerLevel',
                          'walkSprite', 'portrait']
        write_csv('roles.csv', self.roles, fieldnames)

        # 保存职业技能
        fieldnames = get_fieldnames('role_skills.csv')
        if not fieldnames:
            fieldnames = ['roleId', 'skillId', 'learnLevel']
        write_csv('role_skills.csv', self.role_skills, fieldnames)

        messagebox.showinfo("提示", "职业数据已保存")
