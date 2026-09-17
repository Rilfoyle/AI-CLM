# -*- coding: utf-8 -*-
"""Generate the four original TuriX demo contract templates.

The documents reproduce only the generic information structure of a CLM
template. All names, identifiers and clauses are original fictitious content.
They are product-demo fixtures, not legal advice or customer source material.

Selected document preset: ``contract_negotiation_brief``.
Named, consistently applied overrides:
  * ``china_contract_paper``: A4, 25 mm margins, 9072 DXA content width.
  * ``legal_black_hierarchy``: black Chinese contract headings and centered title.
  * ``unicode_body``: Arial Unicode MS, 11 pt, 1.25 line spacing.
First-page pattern: ``memo_masthead`` with a centered legal-title override.

Output: <repo>/runtime/demo-docs/*.docx
Usage: python scripts/gen_demo_docs.py
"""

from __future__ import annotations

import hashlib
import json
import os
import shutil
import subprocess
import tempfile
import uuid
from functools import lru_cache
from pathlib import Path
from typing import Iterable
from zipfile import ZIP_DEFLATED, ZipFile

from docx import Document
from docx.enum.table import WD_CELL_VERTICAL_ALIGNMENT, WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Inches, Pt, RGBColor
from lxml import etree

REPO_ROOT = Path(__file__).resolve().parent.parent
OUT_DIR = REPO_ROOT / "runtime" / "demo-docs"

CONTENT_WIDTH_DXA = 9072
TABLE_INDENT_DXA = 120
CELL_MARGIN_TOP_BOTTOM_DXA = 80
CELL_MARGIN_START_END_DXA = 120
INK = RGBColor(24, 24, 27)
MUTED = RGBColor(98, 105, 118)
ACCENT = RGBColor(30, 92, 154)
PALE_BLUE = "EDF4FB"
PALE_GRAY = "F5F6F8"
DOC_FONT = "Source Han Sans CN"
EMBEDDED_FONT_CANDIDATES = {
    "regular": [
        Path.home() / "Library/Fonts/SourceHanSansCN-Regular.otf",
        Path("/Library/Fonts/SourceHanSansCN-Regular.otf"),
    ],
    "bold": [
        Path.home() / "Library/Fonts/SourceHanSansCN-Bold.otf",
        Path("/Library/Fonts/SourceHanSansCN-Bold.otf"),
    ],
}

W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
R_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
PKG_REL_NS = "http://schemas.openxmlformats.org/package/2006/relationships"
CONTENT_TYPE_NS = "http://schemas.openxmlformats.org/package/2006/content-types"
FONT_REL_TYPE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/font"
OBFUSCATED_FONT_TYPE = "application/vnd.openxmlformats-officedocument.obfuscatedFont"


TEMPLATES = [
    {
        "filename": "物资采购合同范本.docx",
        "title": "物资采购合同",
        "type_code": "DEMO_PURCHASE",
        "purpose": "适用于企业日常物资、设备及配套交付的标准采购场景",
        "party_a": "采购方",
        "party_b": "供货方",
        "recital": "双方基于平等、自愿和诚实信用原则，就物资采购、交付、验收及售后服务事项达成如下约定。",
        "transaction_rows": [
            ("采购标的", "【名称、规格、数量，以经确认的采购清单为准】"),
            ("合同金额", "人民币（含税）【待填写】元；币种：人民币"),
            ("交付安排", "【交付日期】前送达【交付地点】"),
            ("付款条件", "预付款【待填写】%；验收后【待填写】%；质保金【待填写】%"),
        ],
        "clauses": [
            ("标的与质量", [
                "供货方应提供全新、来源合法且符合双方确认规格的货物，并随货提交必要的合格证明、说明资料和装箱清单。",
                "采购清单、技术要求及经双方书面确认的变更记录均为本合同组成部分；文件不一致时，以时间在后的书面确认内容为准。",
            ]),
            ("价格与付款", [
                "合同价款已包含包装、运输、保险、税费以及交付至约定地点所需的合理费用。供货方应在付款条件成就前提供合法有效的等额发票。",
                "采购方在收到合格付款资料后按约定账期支付。付款不代表对货物质量、数量或供货方责任的最终确认。",
            ]),
            ("交付与验收", [
                "供货方应按约定时间和地点完成交付。采购方在合理期限内依据采购清单和技术要求进行验收，并将明显不符合项书面通知供货方。",
                "验收不合格的，供货方应在通知期限内完成补足、修复或更换；由此产生的合理费用由供货方承担。",
            ]),
            ("质量保证与服务", [
                "质保期自最终验收合格之日起计算。质保期内因产品本身原因发生故障的，供货方应及时响应并提供修复、更换或其他双方认可的补救。",
            ]),
            ("重大承诺", [
                "供货方承诺货物权属清晰，不存在已知权利瑕疵；双方承诺不以商业贿赂、不当利益或其他违法方式促成本交易。",
            ]),
            ("保密与数据", [
                "一方因履行合同获得的对方非公开业务、技术和经营信息，仅限用于本合同目的，并应采取不低于保护自身同类信息的合理措施。",
            ]),
            ("违约、争议与其他", [
                "一方未按约履行且在合理补救期内仍未纠正的，应赔偿对方因此遭受的可证明直接损失。争议应先友好协商；协商不成的，向有管辖权的人民法院处理。",
                "本合同的补充或变更应由双方以可追溯的书面形式确认。本模板须经企业内部审批完成后方可进入签署安排。",
            ]),
        ],
    },
    {
        "filename": "技术服务合同范本.docx",
        "title": "技术服务合同",
        "type_code": "DEMO_TECH_SERVICE",
        "purpose": "适用于咨询、实施、运维及其他以成果或里程碑验收的服务场景",
        "party_a": "委托方",
        "party_b": "服务方",
        "recital": "委托方拟采购约定的技术服务，服务方具备相应的专业能力，双方就服务范围、交付成果和验收标准达成如下约定。",
        "transaction_rows": [
            ("服务内容", "【服务范围、里程碑和交付物，详见服务说明】"),
            ("服务费用", "人民币（含税）【待填写】元；币种：人民币"),
            ("服务期间", "自【开始日期】至【结束日期】"),
            ("付款条件", "按里程碑验收结果分期支付，具体比例【待填写】"),
        ],
        "clauses": [
            ("服务范围与组织", [
                "服务方应按经双方确认的服务说明、项目计划和质量标准组织实施，并指定具备相应经验的项目负责人。",
                "未经委托方书面同意，服务方不得将主要服务整体转委托；经同意的转委托不减轻服务方对交付质量和合规性的责任。",
            ]),
            ("里程碑与验收", [
                "服务方应按计划提交可检验的阶段成果。委托方在约定期限内依据验收标准提出确认意见或具体改进事项。",
                "成果不符合约定的，服务方应在合理期限内完成修订并再次提交；因委托方未及时提供必要资料导致的合理延期由双方书面确认。",
            ]),
            ("费用与结算", [
                "除另有约定外，服务费用已覆盖人员、工具、差旅及完成交付所需的合理支出。服务方应在每期付款前提供合格发票和对应的验收依据。",
            ]),
            ("知识产权", [
                "双方各自在合同订立前拥有的技术、材料和工具仍归原权利人所有。为本项目专门形成的交付成果及其使用权归属，以交易信息或附件约定为准。",
                "服务方应确保其交付内容有合法来源；如使用第三方材料，应事先披露必要的许可条件，不得擅自引入限制委托方正常使用的条款。",
            ]),
            ("重大承诺", [
                "服务方承诺按适用法律和双方的信息安全要求处理项目数据，不将业务数据用于模型训练、对外展示或本合同目的之外的用途。",
            ]),
            ("保密与人员管理", [
                "接触保密信息的人员应受不低于本合同的保密义务约束。项目结束或委托方要求时，服务方应按约返还或安全删除相关资料。",
            ]),
            ("违约、争议与其他", [
                "一方严重违反约定且经书面催告仍未补救的，守约方可终止未履行部分并主张可证明的直接损失。争议应先协商，协商不成的依法处理。",
                "本模板须与服务说明、里程碑和验收标准一并完成企业内部审批后，方可进入签署安排。",
            ]),
        ],
    },
    {
        "filename": "产品销售合同范本.docx",
        "title": "产品销售合同",
        "type_code": "DEMO_SALES",
        "purpose": "适用于企业标准产品销售、交付、收款及售后服务场景",
        "party_a": "销售方",
        "party_b": "购买方",
        "recital": "销售方拟向购买方销售约定产品，购买方同意按约受领并支付价款，双方就产品、交付和售后事项达成如下约定。",
        "transaction_rows": [
            ("销售产品", "【名称、型号、数量，以经确认的销售清单为准】"),
            ("合同金额", "人民币（含税）【待填写】元；币种：人民币"),
            ("交付安排", "【交付日期】前送达【交付地点】"),
            ("收款条件", "预付款【待填写】%；交付后【待填写】%；其他【待填写】"),
        ],
        "clauses": [
            ("产品与订单", [
                "产品名称、型号、数量、单价和配置以双方确认的销售清单为准。销售方应交付符合约定且具备合法来源的产品。",
                "购买方提出变更需求时，双方应确认对价格、进度和库存的影响；未经确认的口头变更不构成销售方的交付义务。",
            ]),
            ("价款与结算", [
                "购买方应按约定节点付款。销售方在收款前提供相应的合法有效发票；双方对账差异不影响无争议部分的按期支付。",
            ]),
            ("交付、检验与风险", [
                "销售方按约定方式交付产品，购买方应及时核对数量、外观及随附资料。发现明显不符合项的，应在合理检验期内书面提出。",
                "产品毁损、灭失风险自双方确认的交付节点转移，但风险转移不影响销售方应承担的质量保证责任。",
            ]),
            ("售后服务", [
                "销售方按产品说明和销售清单提供质保及合理技术支持。因购买方不当使用、擅自改装或超出产品条件使用导致的问题不属于免费质保范围。",
            ]),
            ("重大承诺", [
                "双方承诺交易信息真实完整，不通过虚构订单、拆分交易或其他方式规避企业内部审批和适用的监管要求。",
            ]),
            ("品牌与保密", [
                "未经另一方书面许可，任何一方不得以对方名义发布宣传或作出超出合同范围的承诺。双方对履约中知悉的非公开信息承担合理保密义务。",
            ]),
            ("违约、争议与其他", [
                "一方未按约履行的，应在收到书面通知后及时补救；未能补救并给对方造成损失的，应承担可证明的直接损失。争议应先协商，协商不成的依法处理。",
                "本模板与销售清单应共同完成企业内部审批后，方可进入签署安排。",
            ]),
        ],
    },
    {
        "filename": "标准保密协议范本.docx",
        "title": "双向保密协议",
        "type_code": "DEMO_NDA",
        "purpose": "适用于商务洽谈、技术交流或项目评估前的双向信息披露场景",
        "party_a": "披露方／接收方 A",
        "party_b": "披露方／接收方 B",
        "recital": "双方拟就潜在合作事项开展交流，并可能互相披露非公开信息，现就保密信息的使用、保护与返还达成如下约定。",
        "transaction_rows": [
            ("合作事项", "【项目或洽谈主题】"),
            ("披露目的", "仅用于评估、推进和执行上述合作事项"),
            ("保密期限", "自首次披露之日起【待填写】年"),
            ("协议期间", "自【开始日期】至【结束日期】"),
        ],
        "clauses": [
            ("保密信息", [
                "保密信息是指一方以书面、口头、电子或其他形式披露，且已标明保密或依其性质、披露情境应合理理解为非公开的信息。",
                "已为公众合法知悉、接收方在披露前已合法掌握、从无保密义务的第三方合法取得，或由接收方独立开发的信息不属于保密信息。",
            ]),
            ("使用限制", [
                "接收方仅可为约定的披露目的使用保密信息，不得用于竞争分析、逆向工程、模型训练或未经许可的对外展示。",
                "接收方仅向确有知悉必要且受相应保密义务约束的人员披露，并对该等人员的合规使用承担管理责任。",
            ]),
            ("保护措施", [
                "接收方应采取不低于保护自身同类重要信息的合理措施，防止保密信息被未经授权地访问、复制、传输、修改或披露。",
                "发生或可能发生信息泄露时，接收方应及时通知披露方，采取合理止损措施，并配合查明影响范围。",
            ]),
            ("依法披露", [
                "接收方因法律、监管或有权机关要求必须披露时，应在法律允许范围内提前通知披露方，并将披露范围限制在必要限度。",
            ]),
            ("返还与删除", [
                "披露方提出书面要求或合作事项终止后，接收方应按要求返还、删除或销毁保密信息及其复制件；依法必须保留的备份继续受本协议约束。",
            ]),
            ("权利保留", [
                "信息披露不构成知识产权转让、许可或任何交易承诺。任何进一步合作均以双方另行签署并完成内部审批的正式文件为准。",
            ]),
            ("责任、争议与其他", [
                "违反保密义务的一方应及时停止违约、采取补救措施，并赔偿对方因此遭受的可证明直接损失。争议应先协商，协商不成的依法处理。",
                "本模板须完成企业内部审批后方可进入签署安排；任何一方均无义务仅因本协议而继续推进交易。",
            ]),
        ],
    },
]


def set_run_font(run, *, size: float = 11, bold: bool = False, color: RGBColor = INK):
    run.font.name = DOC_FONT
    r_fonts = run._element.get_or_add_rPr().get_or_add_rFonts()
    r_fonts.set(qn("w:ascii"), DOC_FONT)
    r_fonts.set(qn("w:hAnsi"), DOC_FONT)
    r_fonts.set(qn("w:eastAsia"), DOC_FONT)
    run.font.size = Pt(size)
    run.bold = bold
    run.font.color.rgb = color


def set_cell_shading(cell, fill: str):
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = tc_pr.find(qn("w:shd"))
    if shd is None:
        shd = OxmlElement("w:shd")
        tc_pr.append(shd)
    shd.set(qn("w:fill"), fill)


def set_cell_margins(cell):
    tc_pr = cell._tc.get_or_add_tcPr()
    tc_mar = tc_pr.first_child_found_in("w:tcMar")
    if tc_mar is None:
        tc_mar = OxmlElement("w:tcMar")
        tc_pr.append(tc_mar)
    for edge, value in (
        ("top", CELL_MARGIN_TOP_BOTTOM_DXA),
        ("bottom", CELL_MARGIN_TOP_BOTTOM_DXA),
        ("start", CELL_MARGIN_START_END_DXA),
        ("end", CELL_MARGIN_START_END_DXA),
    ):
        node = tc_mar.find(qn(f"w:{edge}"))
        if node is None:
            node = OxmlElement(f"w:{edge}")
            tc_mar.append(node)
        node.set(qn("w:w"), str(value))
        node.set(qn("w:type"), "dxa")


def set_table_geometry(table, widths_dxa: list[int], *, indent_dxa: int = TABLE_INDENT_DXA):
    if sum(widths_dxa) != CONTENT_WIDTH_DXA:
        raise ValueError(f"table widths must total {CONTENT_WIDTH_DXA}: {widths_dxa}")
    table.autofit = False
    table.alignment = WD_TABLE_ALIGNMENT.LEFT
    tbl_pr = table._tbl.tblPr
    tbl_w = tbl_pr.first_child_found_in("w:tblW")
    tbl_w.set(qn("w:w"), str(CONTENT_WIDTH_DXA))
    tbl_w.set(qn("w:type"), "dxa")
    tbl_ind = tbl_pr.first_child_found_in("w:tblInd")
    if tbl_ind is None:
        tbl_ind = OxmlElement("w:tblInd")
        tbl_pr.append(tbl_ind)
    tbl_ind.set(qn("w:w"), str(indent_dxa))
    tbl_ind.set(qn("w:type"), "dxa")
    layout = tbl_pr.first_child_found_in("w:tblLayout")
    if layout is None:
        layout = OxmlElement("w:tblLayout")
        tbl_pr.append(layout)
    layout.set(qn("w:type"), "fixed")

    grid = table._tbl.tblGrid
    for child in list(grid):
        grid.remove(child)
    for width in widths_dxa:
        col = OxmlElement("w:gridCol")
        col.set(qn("w:w"), str(width))
        grid.append(col)

    for row in table.rows:
        for index, cell in enumerate(row.cells):
            cell.width = Inches(widths_dxa[index] / 1440)
            tc_w = cell._tc.get_or_add_tcPr().first_child_found_in("w:tcW")
            tc_w.set(qn("w:w"), str(widths_dxa[index]))
            tc_w.set(qn("w:type"), "dxa")
            set_cell_margins(cell)


def add_page_field(paragraph):
    run = paragraph.add_run()
    begin = OxmlElement("w:fldChar")
    begin.set(qn("w:fldCharType"), "begin")
    instruction = OxmlElement("w:instrText")
    instruction.set(qn("xml:space"), "preserve")
    instruction.text = " PAGE "
    separate = OxmlElement("w:fldChar")
    separate.set(qn("w:fldCharType"), "separate")
    text = OxmlElement("w:t")
    text.text = "1"
    end = OxmlElement("w:fldChar")
    end.set(qn("w:fldCharType"), "end")
    run._r.extend([begin, instruction, separate, text, end])
    set_run_font(run, size=9, color=MUTED)


def configure_document(doc: Document, short_title: str):
    section = doc.sections[0]
    section.page_width = Cm(21.0)
    section.page_height = Cm(29.7)
    section.top_margin = Cm(2.5)
    section.bottom_margin = Cm(2.5)
    section.left_margin = Cm(2.5)
    section.right_margin = Cm(2.5)
    section.header_distance = Cm(1.25)
    section.footer_distance = Cm(1.25)

    normal = doc.styles["Normal"]
    normal.font.name = DOC_FONT
    normal._element.rPr.rFonts.set(qn("w:ascii"), DOC_FONT)
    normal._element.rPr.rFonts.set(qn("w:hAnsi"), DOC_FONT)
    normal._element.rPr.rFonts.set(qn("w:eastAsia"), DOC_FONT)
    normal.font.size = Pt(11)
    normal.font.color.rgb = INK
    normal.paragraph_format.space_before = Pt(0)
    normal.paragraph_format.space_after = Pt(6)
    normal.paragraph_format.line_spacing = 1.25
    normal.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY

    heading = doc.styles["Heading 1"]
    heading.font.name = DOC_FONT
    heading._element.rPr.rFonts.set(qn("w:ascii"), DOC_FONT)
    heading._element.rPr.rFonts.set(qn("w:hAnsi"), DOC_FONT)
    heading._element.rPr.rFonts.set(qn("w:eastAsia"), DOC_FONT)
    heading.font.size = Pt(12)
    heading.font.bold = True
    heading.font.color.rgb = INK
    heading.paragraph_format.space_before = Pt(11)
    heading.paragraph_format.space_after = Pt(6)
    heading.paragraph_format.line_spacing = 1.0
    heading.paragraph_format.keep_with_next = True

    header_p = section.header.paragraphs[0]
    header_p.alignment = WD_ALIGN_PARAGRAPH.LEFT
    header_p.paragraph_format.space_after = Pt(0)
    left = header_p.add_run("TuriX 合同管理系统  ·  演示范本")
    set_run_font(left, size=9, bold=True, color=MUTED)
    right = header_p.add_run(f"    {short_title}")
    set_run_font(right, size=9, color=MUTED)

    footer_p = section.footer.paragraphs[0]
    footer_p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    footer_p.paragraph_format.space_before = Pt(0)
    footer_p.paragraph_format.space_after = Pt(0)
    prefix = footer_p.add_run("仅用于产品演示  ·  第 ")
    set_run_font(prefix, size=9, color=MUTED)
    add_page_field(footer_p)
    suffix = footer_p.add_run(" 页")
    set_run_font(suffix, size=9, color=MUTED)


def next_numbering_ids(doc: Document) -> tuple[int, int]:
    numbering = doc.part.numbering_part.element
    abstract_ids = [int(node.get(qn("w:abstractNumId"))) for node in numbering.findall(qn("w:abstractNum"))]
    num_ids = [int(node.get(qn("w:numId"))) for node in numbering.findall(qn("w:num"))]
    return (max(abstract_ids, default=-1) + 1, max(num_ids, default=0) + 1)


def add_clause_numbering(doc: Document) -> int:
    numbering = doc.part.numbering_part.element
    abstract_id, num_id = next_numbering_ids(doc)
    abstract = OxmlElement("w:abstractNum")
    abstract.set(qn("w:abstractNumId"), str(abstract_id))
    multi = OxmlElement("w:multiLevelType")
    multi.set(qn("w:val"), "singleLevel")
    abstract.append(multi)
    level = OxmlElement("w:lvl")
    level.set(qn("w:ilvl"), "0")
    start = OxmlElement("w:start")
    start.set(qn("w:val"), "1")
    num_fmt = OxmlElement("w:numFmt")
    num_fmt.set(qn("w:val"), "decimal")
    level_text = OxmlElement("w:lvlText")
    level_text.set(qn("w:val"), "第%1条")
    suffix = OxmlElement("w:suff")
    suffix.set(qn("w:val"), "space")
    p_pr = OxmlElement("w:pPr")
    ind = OxmlElement("w:ind")
    ind.set(qn("w:left"), "0")
    ind.set(qn("w:hanging"), "0")
    p_pr.append(ind)
    level.extend([start, num_fmt, level_text, suffix, p_pr])
    abstract.append(level)
    numbering.append(abstract)
    num = OxmlElement("w:num")
    num.set(qn("w:numId"), str(num_id))
    abstract_ref = OxmlElement("w:abstractNumId")
    abstract_ref.set(qn("w:val"), str(abstract_id))
    num.append(abstract_ref)
    numbering.append(num)
    return num_id


def apply_numbering(paragraph, num_id: int):
    p_pr = paragraph._p.get_or_add_pPr()
    num_pr = OxmlElement("w:numPr")
    ilvl = OxmlElement("w:ilvl")
    ilvl.set(qn("w:val"), "0")
    num_id_node = OxmlElement("w:numId")
    num_id_node.set(qn("w:val"), str(num_id))
    num_pr.extend([ilvl, num_id_node])
    p_pr.append(num_pr)


def add_text_paragraph(doc: Document, text: str):
    paragraph = doc.add_paragraph()
    paragraph.paragraph_format.first_line_indent = Pt(22)
    paragraph.paragraph_format.space_before = Pt(0)
    paragraph.paragraph_format.space_after = Pt(6)
    paragraph.paragraph_format.line_spacing = 1.25
    paragraph.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
    run = paragraph.add_run(text)
    set_run_font(run)
    return paragraph


def add_label_value_table(doc: Document, rows: Iterable[tuple[str, str]], *, fill: str = PALE_GRAY):
    table = doc.add_table(rows=0, cols=2)
    table.style = "Table Grid"
    for label, value in rows:
        cells = table.add_row().cells
        cells[0].vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER
        cells[1].vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER
        set_cell_shading(cells[0], fill)
        for cell in cells:
            paragraph = cell.paragraphs[0]
            paragraph.paragraph_format.space_before = Pt(0)
            paragraph.paragraph_format.space_after = Pt(2)
            paragraph.paragraph_format.line_spacing = 1.15
        label_run = cells[0].paragraphs[0].add_run(label)
        set_run_font(label_run, size=10.5, bold=True)
        value_run = cells[1].paragraphs[0].add_run(value)
        set_run_font(value_run, size=10.5)
    set_table_geometry(table, [1700, 7372])
    spacer = doc.add_paragraph()
    spacer.paragraph_format.space_after = Pt(0)
    return table


def add_title_block(doc: Document, spec: dict):
    kicker = doc.add_paragraph()
    kicker.alignment = WD_ALIGN_PARAGRAPH.CENTER
    kicker.paragraph_format.space_before = Pt(10)
    kicker.paragraph_format.space_after = Pt(4)
    run = kicker.add_run("TURIX · 标准合同范本")
    set_run_font(run, size=9.5, bold=True, color=ACCENT)

    title = doc.add_paragraph()
    title.alignment = WD_ALIGN_PARAGRAPH.CENTER
    title.paragraph_format.space_before = Pt(0)
    title.paragraph_format.space_after = Pt(5)
    title.paragraph_format.keep_with_next = True
    run = title.add_run(spec["title"])
    set_run_font(run, size=22, bold=True, color=INK)
    run._element.get_or_add_rPr().get_or_add_rFonts().set(qn("w:eastAsia"), DOC_FONT)

    subtitle = doc.add_paragraph()
    subtitle.alignment = WD_ALIGN_PARAGRAPH.CENTER
    subtitle.paragraph_format.space_before = Pt(0)
    subtitle.paragraph_format.space_after = Pt(14)
    run = subtitle.add_run(f"原创演示模板 · V1.0 · {spec['type_code']}")
    set_run_font(run, size=9.5, color=MUTED)

    add_label_value_table(
        doc,
        [
            ("适用场景", spec["purpose"]),
            ("合同编号", "【提交审批后由系统生成】"),
            ("审批状态", "【草稿／协同中／审批中／审批完成】"),
        ],
        fill=PALE_BLUE,
    )


def add_section_table(doc: Document, title_text: str, rows: Iterable[tuple[str, str]]):
    heading = doc.add_paragraph()
    heading.paragraph_format.space_before = Pt(8)
    heading.paragraph_format.space_after = Pt(6)
    heading.paragraph_format.keep_with_next = True
    run = heading.add_run(title_text)
    set_run_font(run, size=12, bold=True)
    run._element.get_or_add_rPr().get_or_add_rFonts().set(qn("w:eastAsia"), DOC_FONT)
    add_label_value_table(doc, rows)


def add_signature_placeholder(doc: Document, spec: dict):
    heading = doc.add_paragraph()
    heading.paragraph_format.space_before = Pt(12)
    heading.paragraph_format.space_after = Pt(6)
    heading.paragraph_format.keep_with_next = True
    run = heading.add_run("审批完成后的签署信息（本期系统不执行签署）")
    set_run_font(run, size=11, bold=True, color=MUTED)
    table = doc.add_table(rows=3, cols=2)
    table.style = "Table Grid"
    values = [
        (f"{spec['party_a']}：________________", f"{spec['party_b']}：________________"),
        ("授权代表：________________", "授权代表：________________"),
        ("日期：____年__月__日", "日期：____年__月__日"),
    ]
    for row, values_in_row in zip(table.rows, values):
        for cell, value in zip(row.cells, values_in_row):
            paragraph = cell.paragraphs[0]
            paragraph.paragraph_format.space_before = Pt(0)
            paragraph.paragraph_format.space_after = Pt(5)
            paragraph.paragraph_format.line_spacing = 1.15
            run = paragraph.add_run(value)
            set_run_font(run, size=10.5)
    set_table_geometry(table, [4536, 4536])


def _find_font_file(weight: str) -> Path | None:
    return next((candidate for candidate in EMBEDDED_FONT_CANDIDATES[weight] if candidate.exists()), None)


@lru_cache(maxsize=4)
def _load_embeddable_font(font_path_text: str) -> bytes:
    font_path = Path(font_path_text)
    subsetter = shutil.which("hb-subset")
    if not subsetter:
        return font_path.read_bytes()
    static_text = (
        "TuriX 合同管理系统 演示范本 标准合同 原创模板 版本 编号 状态 草稿 协同中 审批中 审批完成 "
        "第一二三四五六七八九十条年月日 ABCDEFGHIJKLMNOPQRSTUVWXYZ "
        "abcdefghijklmnopqrstuvwxyz 0123456789 _-—·，。；：！？【】（）％%／/\\________________"
    )
    subset_text = static_text + json.dumps(TEMPLATES, ensure_ascii=False, sort_keys=True)
    with tempfile.TemporaryDirectory(prefix="turix-font-subset-") as temporary_dir:
        output_path = Path(temporary_dir) / font_path.name
        process = subprocess.run(
            [subsetter, str(font_path), f"--text={subset_text}", f"--output-file={output_path}"],
            check=False,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )
        if process.returncode != 0 or not output_path.exists():
            raise RuntimeError(f"font subset failed for {font_path}: {process.stderr.strip()}")
        return output_path.read_bytes()


def _obfuscate_font(font_bytes: bytes, font_key: uuid.UUID) -> bytes:
    """Apply the ECMA-376 first-32-byte font obfuscation used by .odttf parts."""
    payload = bytearray(font_bytes)
    key = font_key.bytes_le
    for index in range(min(32, len(payload))):
        payload[index] ^= key[index % 16]
    return bytes(payload)


def embed_demo_fonts(path: Path):
    """Embed OFL Source Han Sans so headless preview and Word render Chinese reliably."""
    font_paths = {weight: _find_font_file(weight) for weight in ("regular", "bold")}
    if not font_paths["regular"]:
        # The DOCX remains standards-compliant and Word can substitute the named font.
        # Current macOS demo setup has the OFL font, so verified artifacts embed it.
        return

    with ZipFile(path, "r") as archive:
        parts = {name: archive.read(name) for name in archive.namelist()}

    font_table = etree.fromstring(parts["word/fontTable.xml"])
    font_node = font_table.xpath(f"./w:font[@w:name='{DOC_FONT}']", namespaces={"w": W_NS})
    if font_node:
        font_node = font_node[0]
    else:
        font_node = etree.SubElement(font_table, f"{{{W_NS}}}font")
        font_node.set(f"{{{W_NS}}}name", DOC_FONT)

    rels_name = "word/_rels/fontTable.xml.rels"
    if rels_name in parts:
        rels = etree.fromstring(parts[rels_name])
    else:
        rels = etree.Element(f"{{{PKG_REL_NS}}}Relationships", nsmap={None: PKG_REL_NS})
    existing_ids = []
    for relation in rels:
        relation_id = relation.get("Id", "")
        if relation_id.startswith("rId") and relation_id[3:].isdigit():
            existing_ids.append(int(relation_id[3:]))
    next_rel_id = max(existing_ids, default=0) + 1

    for weight, tag_name in (("regular", "embedRegular"), ("bold", "embedBold")):
        font_path = font_paths[weight] or font_paths["regular"]
        font_bytes = _load_embeddable_font(str(font_path))
        digest = hashlib.sha256(font_bytes + weight.encode("ascii")).digest()
        font_key = uuid.UUID(bytes=digest[:16])
        relationship_id = f"rId{next_rel_id}"
        next_rel_id += 1
        part_name = f"word/fonts/turix-{weight}.odttf"
        parts[part_name] = _obfuscate_font(font_bytes, font_key)

        relation = etree.SubElement(rels, f"{{{PKG_REL_NS}}}Relationship")
        relation.set("Id", relationship_id)
        relation.set("Type", FONT_REL_TYPE)
        relation.set("Target", f"fonts/turix-{weight}.odttf")

        existing = font_node.find(f"{{{W_NS}}}{tag_name}")
        if existing is None:
            existing = etree.SubElement(font_node, f"{{{W_NS}}}{tag_name}")
        existing.set(f"{{{R_NS}}}id", relationship_id)
        existing.set(f"{{{W_NS}}}fontKey", "{" + str(font_key).upper() + "}")

    content_types = etree.fromstring(parts["[Content_Types].xml"])
    defaults = content_types.findall(f"{{{CONTENT_TYPE_NS}}}Default")
    if not any(node.get("Extension") == "odttf" for node in defaults):
        default = etree.SubElement(content_types, f"{{{CONTENT_TYPE_NS}}}Default")
        default.set("Extension", "odttf")
        default.set("ContentType", OBFUSCATED_FONT_TYPE)

    parts["word/fontTable.xml"] = etree.tostring(
        font_table, xml_declaration=True, encoding="UTF-8", standalone="yes"
    )
    parts[rels_name] = etree.tostring(rels, xml_declaration=True, encoding="UTF-8", standalone="yes")
    parts["[Content_Types].xml"] = etree.tostring(
        content_types, xml_declaration=True, encoding="UTF-8", standalone="yes"
    )

    temporary = path.with_suffix(".font-embed.tmp")
    with ZipFile(temporary, "w", compression=ZIP_DEFLATED) as archive:
        for name, payload in parts.items():
            archive.writestr(name, payload)
    os.replace(temporary, path)


def build_template(spec: dict) -> Path:
    doc = Document()
    configure_document(doc, spec["title"])
    doc.core_properties.title = spec["title"]
    doc.core_properties.subject = "TuriX 原创合同演示范本"
    doc.core_properties.author = "TuriX"
    doc.core_properties.keywords = "TuriX, CLM, demo, original"
    doc.core_properties.comments = "仅用于 TuriX 产品演示，不构成法律意见。"
    add_title_block(doc, spec)
    add_section_table(
        doc,
        "合同主体",
        [
            (spec["party_a"], "【主体名称】；统一社会信用代码：【待填写】"),
            (spec["party_b"], "【主体名称】；统一社会信用代码：【待填写】"),
            ("业务归属", "签订部门：【待填写】；业务负责人：【待填写】"),
        ],
    )
    add_section_table(doc, "交易信息", spec["transaction_rows"])
    add_text_paragraph(doc, spec["recital"])

    num_id = add_clause_numbering(doc)
    for heading_text, paragraphs in spec["clauses"]:
        heading = doc.add_paragraph(style="Heading 1")
        apply_numbering(heading, num_id)
        run = heading.add_run(heading_text)
        set_run_font(run, size=12, bold=True)
        run._element.get_or_add_rPr().get_or_add_rFonts().set(qn("w:eastAsia"), DOC_FONT)
        for paragraph_text in paragraphs:
            add_text_paragraph(doc, paragraph_text)

    add_signature_placeholder(doc, spec)
    final_note = doc.add_paragraph()
    final_note.alignment = WD_ALIGN_PARAGRAPH.CENTER
    final_note.paragraph_format.space_before = Pt(8)
    final_note.paragraph_format.space_after = Pt(0)
    run = final_note.add_run("— 本模板全部内容均为 TuriX 原创虚构演示数据 —")
    set_run_font(run, size=9, color=MUTED)

    path = OUT_DIR / spec["filename"]
    doc.save(path)
    embed_demo_fonts(path)
    return path


def audit_document(path: Path):
    doc = Document(path)
    section = doc.sections[0]
    expected = {
        "page_width": round(Cm(21.0).emu),
        "page_height": round(Cm(29.7).emu),
        "margin": round(Cm(2.5).emu),
    }
    actual = {
        "page_width": section.page_width.emu,
        "page_height": section.page_height.emu,
        "margin": section.left_margin.emu,
    }
    # OOXML stores section geometry in twips, so python-docx rounds centimetres.
    if any(abs(actual[key] - expected[key]) > 500 for key in expected):
        raise RuntimeError(f"page geometry mismatch for {path.name}: {actual} != {expected}")
    if len(doc.tables) < 4:
        raise RuntimeError(f"expected structured tables in {path.name}")
    numbering_xml = doc.part.numbering_part.element.xml
    if "第%1条" not in numbering_xml:
        raise RuntimeError(f"real clause numbering missing in {path.name}")
    for table in doc.tables:
        tbl_w = table._tbl.tblPr.first_child_found_in("w:tblW")
        if tbl_w is None or tbl_w.get(qn("w:type")) != "dxa":
            raise RuntimeError(f"fixed table width missing in {path.name}")
    with ZipFile(path, "r") as archive:
        if _find_font_file("regular") and "word/fonts/turix-regular.odttf" not in archive.namelist():
            raise RuntimeError(f"embedded Chinese font missing in {path.name}")


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    expected_names = {spec["filename"] for spec in TEMPLATES}
    for old in OUT_DIR.glob("*.docx"):
        if old.name not in expected_names:
            old.unlink()

    output = []
    for spec in TEMPLATES:
        path = build_template(spec)
        audit_document(path)
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        output.append({"file": str(path), "sha256": digest, "bytes": path.stat().st_size})
    print(json.dumps(output, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
